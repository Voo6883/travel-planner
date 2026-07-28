package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes;
import com.travelplanner.application.account.AccountTestFakes.CapturingMailer;
import com.travelplanner.application.account.AccountTestFakes.FakeMailRateLimits;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.AccountDisabledException;
import com.travelplanner.domain.exception.IdentityAlreadyLinkedException;
import com.travelplanner.domain.exception.ProviderEmailUnavailableException;
import com.travelplanner.domain.exception.ProviderLinkRequiredException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.valueobject.IdentityClaims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR 009 §4, exhaustively.
 *
 * <p>The test this class exists for is
 * {@link #doesNotLinkAVictimsGoogleIdentityIntoAnAccountAnAttackerRegistered}. Everything else here
 * is the surrounding behaviour that must keep working while that one holds.
 *
 * <p>No database and no network: the rules are application-layer decisions, and proving them needs
 * neither.
 */
class AccountLinkingServiceTest {

    private static final String VICTIM_EMAIL = "victim@gmail.com";
    private static final String GOOGLE_SUBJECT = "google-uid-1";

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();
    private final CapturingMailer mailer = new CapturingMailer();

    private final AccountLinkingService linking = new AccountLinkingService(users, identities,
            AccountTestFakes.lifecycleMailer(mailer, new FakeMailRateLimits(), identities));

    // -------------------------------------------------------------------------------------------
    // The pre-hijack rule (ADR 009 §4)
    // -------------------------------------------------------------------------------------------

    @Test
    void doesNotLinkAVictimsGoogleIdentityIntoAnAccountAnAttackerRegistered() {
        // The attack, step by step. An attacker registers locally with an address they do not own,
        // so the verification mail goes to the victim and this row stays unverified.
        User attackerRow = account(VICTIM_EMAIL, "hash:attacker-password", false);

        // The victim later signs in with Google, which asserts the same address, verified.
        assertThatThrownBy(() -> linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true)))
                .isInstanceOf(ProviderLinkRequiredException.class)
                .satisfies(refusal -> assertThat(((ProviderLinkRequiredException) refusal).details())
                        .containsEntry("provider", "FIREBASE_GOOGLE"));

        // Nothing was written. Under email-equality linking the attacker's row would now hold the
        // victim's Google identity, and the attacker's password would open the victim's trips.
        assertThat(identities.findAllByUserId(attackerRow.id())).isEmpty();
        assertThat(identities.saved).isEmpty();
        // And no second account was created behind the attacker's back either.
        assertThat(users.byId).hasSize(1);
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void doesNotAutoLinkWhenTheProviderAddressIsUnverified() {
        // The mirror image: the account is genuine and verified, but the provider will not vouch
        // for the address. Both sides must be verified, not either one.
        User existing = account(VICTIM_EMAIL, "hash:password", true);

        assertThatThrownBy(() -> linking.resolveSignIn(googleClaims(VICTIM_EMAIL, false)))
                .isInstanceOf(ProviderLinkRequiredException.class);

        assertThat(identities.findAllByUserId(existing.id())).isEmpty();
    }

    @Test
    void autoLinksOnlyWhenBothSidesAreVerified() {
        User existing = account(VICTIM_EMAIL, "hash:password", true);

        LinkedAccount resolved = linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true));

        assertThat(resolved.providerLinked()).isTrue();
        assertThat(resolved.newUser()).isFalse();
        assertThat(resolved.user().id()).isEqualTo(existing.id());
        assertThat(providersOf(existing)).containsExactly(AuthProvider.FIREBASE_GOOGLE);
        // Linking is not a registration, so no welcome mail.
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void resolvesByProviderSubjectRatherThanByAddress() {
        // The account's address has since changed. The identity still resolves, because the join
        // key is the provider subject — the whole reason ADR 009 §4 can reject email equality.
        User existing = account("renamed@example.com", "hash:password", true);
        identities.save(identity(existing.id(), AuthProvider.FIREBASE_GOOGLE, GOOGLE_SUBJECT));

        LinkedAccount resolved = linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true));

        assertThat(resolved.user().id()).isEqualTo(existing.id());
        assertThat(resolved.newUser()).isFalse();
        assertThat(resolved.providerLinked()).isFalse();
        assertThat(identities.saved).hasSize(1);
    }

    // -------------------------------------------------------------------------------------------
    // First sign-in (UC-A02, UC-A03)
    // -------------------------------------------------------------------------------------------

    @Test
    void createsAVerifiedPasswordlessAccountAndSendsExactlyOneWelcomeMail() {
        LinkedAccount resolved = linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true));

        assertThat(resolved.newUser()).isTrue();
        assertThat(resolved.providerLinked()).isFalse();

        User created = users.findByEmailIgnoreCase(VICTIM_EMAIL).orElseThrow();
        assertThat(created.emailVerified()).describedAs("Google confirmed the address").isTrue();
        assertThat(created.isOAuthOnly()).describedAs("no local password to guess").isTrue();
        assertThat(created.username()).isNull();
        assertThat(created.role()).isEqualTo(Role.USER);
        assertThat(providersOf(created)).containsExactly(AuthProvider.FIREBASE_GOOGLE);

        // PLAN §4.0.10: welcome on first sign-up only, and no verification mail — the provider
        // already confirmed the address.
        assertThat(mailer.sent).hasSize(1);
        assertThat(mailer.only().to()).isEqualTo(VICTIM_EMAIL);
    }

    @Test
    void sendsNoSecondWelcomeMailToAReturningUser() {
        linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true));
        mailer.sent.clear();

        LinkedAccount second = linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true));

        assertThat(second.newUser()).isFalse();
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void refusesToCreateAnAccountWithNoAddressFromTheProvider() {
        // The GitHub private-email case: the adapter found nothing primary, verified, and routable.
        assertThatThrownBy(() -> linking.resolveSignIn(
                new IdentityClaims(AuthProvider.GITHUB, "gh-1", null, false)))
                .isInstanceOf(ProviderEmailUnavailableException.class);

        assertThat(users.byId).isEmpty();
    }

    @Test
    void signsInAnAddresslessIdentityThatIsAlreadyLinked() {
        // Having made an account privately is not a reason to be locked out of it later.
        User existing = account(VICTIM_EMAIL, "hash:password", true);
        identities.save(identity(existing.id(), AuthProvider.GITHUB, "gh-1"));

        LinkedAccount resolved =
                linking.resolveSignIn(new IdentityClaims(AuthProvider.GITHUB, "gh-1", null, false));

        assertThat(resolved.user().id()).isEqualTo(existing.id());
    }

    @Test
    void refusesADisabledAccount() {
        User disabled = new User(UUID.randomUUID(), null, VICTIM_EMAIL, null, true, Role.USER,
                false, 0, null, null, Instant.now(), Instant.now());
        users.save(disabled);
        identities.save(identity(disabled.id(), AuthProvider.FIREBASE_GOOGLE, GOOGLE_SUBJECT));

        assertThatThrownBy(() -> linking.resolveSignIn(googleClaims(VICTIM_EMAIL, true)))
                .isInstanceOf(AccountDisabledException.class);
    }

    // -------------------------------------------------------------------------------------------
    // Explicit confirmation (UC-A09) — the recovery path from a refused auto-link
    // -------------------------------------------------------------------------------------------

    @Test
    void explicitLinkAttachesTheIdentityToTheSignedInAccountWhateverItsAddress() {
        // The account is unverified, which is exactly when auto-linking refuses. Holding a session
        // is the proof a matching address is not, so this succeeds where the automatic path did not.
        User caller = account("someone-else@example.com", "hash:password", false);

        linking.link(caller.id(), googleClaims(VICTIM_EMAIL, true));

        assertThat(providersOf(caller)).containsExactly(AuthProvider.FIREBASE_GOOGLE);
    }

    @Test
    void oneExternalIdentityNeverLinksToTwoUsers() {
        User first = account("first@example.com", "hash:password", true);
        User second = account("second@example.com", "hash:password", true);
        linking.link(first.id(), googleClaims(VICTIM_EMAIL, true));

        assertThatThrownBy(() -> linking.link(second.id(), googleClaims(VICTIM_EMAIL, true)))
                .isInstanceOf(IdentityAlreadyLinkedException.class);

        assertThat(providersOf(second)).isEmpty();
    }

    @Test
    void relinkingTheSameIdentityToTheSameAccountIsANoOp() {
        User caller = account("first@example.com", "hash:password", true);
        linking.link(caller.id(), googleClaims(VICTIM_EMAIL, true));

        linking.link(caller.id(), googleClaims(VICTIM_EMAIL, true));

        assertThat(identities.findAllByUserId(caller.id())).hasSize(1);
    }

    @Test
    void refusesASecondIdentityForAProviderTheAccountAlreadyHas() {
        // Two Google identities on one account would make `linked_providers` ambiguous and unlink
        // non-deterministic.
        User caller = account("first@example.com", "hash:password", true);
        linking.link(caller.id(), googleClaims(VICTIM_EMAIL, true));

        assertThatThrownBy(() -> linking.link(caller.id(),
                new IdentityClaims(AuthProvider.FIREBASE_GOOGLE, "google-uid-2", VICTIM_EMAIL, true)))
                .isInstanceOf(IdentityAlreadyLinkedException.class);
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    private User account(String email, String passwordHash, boolean emailVerified) {
        Instant now = Instant.now();
        return users.save(new User(UUID.randomUUID(), null, email, passwordHash, emailVerified,
                Role.USER, true, 0, null, null, now, now));
    }

    private static IdentityClaims googleClaims(String email, boolean emailVerified) {
        return new IdentityClaims(AuthProvider.FIREBASE_GOOGLE, GOOGLE_SUBJECT, email, emailVerified);
    }

    private static UserIdentity identity(UUID userId, AuthProvider provider, String subject) {
        return new UserIdentity(UUID.randomUUID(), userId, provider, subject, null, Instant.now());
    }

    private List<AuthProvider> providersOf(User user) {
        return identities.findAllByUserId(user.id()).stream().map(UserIdentity::provider).toList();
    }
}
