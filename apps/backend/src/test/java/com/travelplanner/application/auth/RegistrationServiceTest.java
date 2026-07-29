package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes;
import com.travelplanner.application.account.AccountTestFakes.CapturingMailer;
import com.travelplanner.application.account.AccountTestFakes.FakeAccountTokens;
import com.travelplanner.application.account.AccountTestFakes.FakeMailRateLimits;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.config.MailProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.valueobject.MailMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * UC-A01, with two properties under test.
 *
 * <p><b>ADR 009 §6's uniform response</b> — nothing a caller can observe distinguishes a created
 * account from a collision.
 *
 * <p><b>Task 09's deferred answer</b> — every outcome nonetheless sends a mail to the submitted
 * address, so the person who controls that mailbox does learn what happened. Task 08 left this half
 * undone, and a username collision was consequently a dead end: "check your email", and nothing
 * ever arrived.
 */
class RegistrationServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();
    private final FakeAccountTokens tokens = new FakeAccountTokens();
    private final CapturingMailer mailer = new CapturingMailer();

    /**
     * The default subject under test sends real mail, so registration behaves as UC-A01 describes:
     * the account starts unverified and the link is what proves the address.
     */
    private final RegistrationService registration = registrationWith(MailProperties.RESEND_PROVIDER);

    private RegistrationService registrationWith(String mailProvider) {
        MailProperties mail = new MailProperties();
        mail.setProvider(mailProvider);
        return new RegistrationService(
                users,
                AccountTestFakes.passwordPolicy(),
                new RegistrationOutcomes(identities, AccountTestFakes.tokenService(tokens),
                        AccountTestFakes.lifecycleMailer(mailer, new FakeMailRateLimits(), identities)),
                mail);
    }

    @Test
    void createsAnUnverifiedUserAccountWithTheDefaultRole() {
        registration.register(new RegisterCommand("Aisyah@Example.com", "aisyah", "long-enough-pw"));

        User created = users.byId.values().iterator().next();
        assertThat(created.email()).isEqualTo("aisyah@example.com");
        assertThat(created.username()).isEqualTo("aisyah");
        assertThat(created.passwordHash()).isEqualTo("hash:long-enough-pw");
        assertThat(created.role()).isEqualTo(Role.USER);
        assertThat(created.enabled()).isTrue();
        // UC-A01: unverified until the link is clicked, which UC-A08 turns into a login gate.
        assertThat(created.emailVerified()).isFalse();
        assertThat(created.tokenVersion()).isZero();
        assertThat(created.isDeleted()).isFalse();
    }

    /**
     * With the stub mailer there is no mailbox for the verification link to reach, so holding the
     * account unverified would make sign-up impossible to complete: UC-A08 refuses the login and
     * the only way through is reading the link out of the log at DEBUG.
     *
     * <p>This relaxation is confined to a stub mailer and cannot reach production —
     * {@code MailConfig} fails startup when the provider is still the stub under {@code prod}.
     */
    @Test
    void createsAnAlreadyVerifiedAccountWhenNoRealMailerIsConfigured() {
        RegistrationService stubMailerRegistration = registrationWith(MailProperties.STUB_PROVIDER);

        stubMailerRegistration.register(new RegisterCommand("nomail@example.com", "nomail", "long-enough-pw"));

        User created = users.byId.values().iterator().next();
        assertThat(created.emailVerified()).isTrue();
    }

    @Test
    void storesTheHashRatherThanThePasswordItself() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        // The fake hasher is deliberately reversible so tests can read it; what is asserted here
        // is that the value stored went through the hasher rather than straight into the column.
        // BcryptPasswordHasherTest covers the one-way property of the real implementation.
        assertThat(users.byId.values()).allSatisfy(user ->
                assertThat(user.passwordHash()).isNotEqualTo("long-enough-pw"));
    }

    @Test
    void linksTheLocalProviderSoAuthMeCanListItFromTheFirstMoment() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThat(identities.saved).singleElement().satisfies(identity -> {
            assertThat(identity.provider()).isEqualTo(AuthProvider.LOCAL);
            // The subject is this system's own user id: LOCAL owns the account, so there is no
            // external identifier to carry.
            assertThat(identity.providerSubjectId())
                    .isEqualTo(users.byId.keySet().iterator().next().toString());
        });
    }

    // ---------------------------------------------------------------------------------------
    // UC-N01 and UC-N02 — what a successful sign-up mails
    // ---------------------------------------------------------------------------------------

    @Test
    void sendsAWelcomeAndAVerificationLinkToANewAccount() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        // PLAN §4.0.10's table triggers both on a local registration.
        assertThat(mailer.subjects())
                .containsExactly("Welcome to Travel Planner", "Confirm your email address");
        assertThat(mailer.sent()).allSatisfy(message ->
                assertThat(message.to()).isEqualTo("a@example.com"));
    }

    @Test
    void putsARedeemableTokenInTheVerificationLinkAndNotInTheDatabase() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        UUID userId = users.byId.keySet().iterator().next();
        String rawToken = tokenFromLink(mailer.last());

        // The stored value is a digest, so the mailed token cannot be read back out of the table.
        assertThat(tokens.byId.values()).singleElement().satisfies(stored -> {
            assertThat(stored.userId()).isEqualTo(userId);
            assertThat(stored.tokenHash()).isNotEqualTo(rawToken).hasSize(64);
            assertThat(stored.isConsumed()).isFalse();
            assertThat(stored.expiresAt()).isAfter(Instant.now());
        });
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §6 — collisions stay invisible over HTTP and are explained by mail
    // ---------------------------------------------------------------------------------------

    @Test
    void isSilentAboutADuplicateEmailRatherThanConfirmingTheAccountExists() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        // ADR 009 §6: no exception, no second account, no way for the caller to tell.
        assertThatCode(() ->
                registration.register(new RegisterCommand("A@Example.com", "someone", "another-pw")))
                .doesNotThrowAnyException();

        assertThat(users.byId).hasSize(1);
    }

    @Test
    void tellsTheAddressOwnerByMailThatTheyAlreadyHaveAnAccount() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));
        mailer.sent.clear();

        registration.register(new RegisterCommand("A@Example.com", "someone", "another-pw"));

        assertThat(mailer.only().subject()).isEqualTo("About your Travel Planner sign-up");
        assertThat(mailer.only().textBody()).contains("You already have an account");
        // No new token: a sign-up attempt must not mint anything against somebody else's account.
        assertThat(tokens.byId).hasSize(1);
    }

    @Test
    void isEquallySilentAboutADuplicateUsername() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThatCode(() ->
                registration.register(new RegisterCommand("b@example.com", "AISYAH", "another-pw")))
                .doesNotThrowAnyException();

        assertThat(users.byId).hasSize(1);
    }

    @Test
    void tellsTheSubmittedAddressThatTheUsernameWasTakenSoTheFlowIsNotADeadEnd() {
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));
        mailer.sent.clear();

        registration.register(new RegisterCommand("b@example.com", "AISYAH", "another-pw"));

        // This is the defect task 08 recorded: without this mail the user waits forever for a
        // verification link that was never going to be sent.
        assertThat(mailer.only().to()).isEqualTo("b@example.com");
        assertThat(mailer.only().textBody()).contains("That username is taken").contains("AISYAH");
        assertThat(users.byId).hasSize(1);
    }

    @Test
    void escapesAUsernameBeforeEchoingItIntoTheHtmlBody() {
        // The username is echoed back into a mail sent to a third party, so an unescaped value is
        // script injection into somebody else's inbox.
        users.save(AuthTestFakes.user("taken@example.com", "<script>", "hash:x"));

        registration.register(new RegisterCommand("victim@example.com", "<script>", "another-pw"));

        assertThat(mailer.only().htmlBody()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void tellsAProviderOnlyAccountToSignInWithItsProviderRatherThanOfferingAReset() {
        // ADR 009 §4: password_hash IS NULL. Nothing here may hand out a local credential.
        User oauthOnly = users.save(AuthTestFakes.user("g@example.com", "gmailuser", null));
        identities.save(new com.travelplanner.domain.model.UserIdentity(UUID.randomUUID(),
                oauthOnly.id(), AuthProvider.FIREBASE_GOOGLE, "firebase-uid", oauthOnly.email(),
                Instant.now()));

        registration.register(new RegisterCommand("g@example.com", "someone", "another-pw"));

        assertThat(mailer.only().subject()).isEqualTo("About your Travel Planner account");
        assertThat(mailer.only().textBody()).contains("FIREBASE_GOOGLE");
        assertThat(tokens.byId).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------

    @Test
    void stillRejectsAWeakPasswordBecauseThatRevealsNothingAboutAnyoneElse() {
        assertThatThrownBy(() ->
                registration.register(new RegisterCommand("a@example.com", "aisyah", "short")))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.byId).isEmpty();
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void validatesThePasswordBeforeCheckingWhetherTheAddressIsTaken() {
        // Otherwise the time to respond differs between a taken and a free address, and timing
        // becomes the oracle the uniform response exists to close.
        registration.register(new RegisterCommand("a@example.com", "aisyah", "long-enough-pw"));

        assertThatThrownBy(() ->
                registration.register(new RegisterCommand("a@example.com", "other", "short")))
                .isInstanceOf(ValidationFailedException.class);
    }

    private static String tokenFromLink(MailMessage message) {
        String body = message.textBody();
        int start = body.indexOf("token=") + "token=".length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return body.substring(start, end);
    }
}
