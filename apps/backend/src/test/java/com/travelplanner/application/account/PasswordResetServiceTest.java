package com.travelplanner.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.account.AccountTestFakes.CapturingMailer;
import com.travelplanner.application.account.AccountTestFakes.FakeAccountTokens;
import com.travelplanner.application.account.AccountTestFakes.FakeMailRateLimits;
import com.travelplanner.application.auth.AuthTestFakes;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.InvalidTokenException;
import com.travelplanner.domain.exception.RateLimitedException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-A07 — forgot password, the OAuth-only rule (ADR 009 §4), and reset-time revocation. */
class PasswordResetServiceTest {

    private static final String EMAIL = "aisyah@example.com";
    private static final String IP = "203.0.113.7";

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();
    private final FakeAccountTokens tokens = new FakeAccountTokens();
    private final FakeMailRateLimits limits = new FakeMailRateLimits();
    private final CapturingMailer mailer = new CapturingMailer();

    private final AccountStore accounts = AccountTestFakes.accountStore(users);
    private final PasswordResetService service = new PasswordResetService(
            accounts,
            AccountTestFakes.tokenService(tokens),
            AccountTestFakes.lifecycleMailer(mailer, limits, identities));

    // -------------------------------------------------------------------------------------
    // forgot — uniform, and never an existence oracle
    // -------------------------------------------------------------------------------------

    @Test
    void mailsAResetLinkToAnAccountThatHasALocalPassword() {
        User account = givenLocalAccount();

        service.forgot(EMAIL, IP);

        assertThat(mailer.only().subject()).isEqualTo("Reset your Travel Planner password");
        assertThat(mailer.only().to()).isEqualTo(EMAIL);
        assertThat(tokens.liveFor(account.id(), AccountTokenPurpose.PASSWORD_RESET)).isPresent();
    }

    @Test
    void staysSilentAndMailsNothingForAnAddressThatIsNotRegistered() {
        // No exception — the response must be identical to the registered case (ADR 009 §6) — and
        // no mail, because "you have no account here" sent to any address on request would make
        // this endpoint a way to mail strangers.
        assertThatCode(() -> service.forgot("nobody@example.com", IP)).doesNotThrowAnyException();

        assertThat(mailer.sent).isEmpty();
        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void staysSilentForADisabledOrDeletedAccount() {
        User disabled = users.save(new User(UUID.randomUUID(), "off", "off@example.com", "hash:x",
                true, com.travelplanner.domain.enums.Role.USER, false, 0, null, null,
                Instant.now(), Instant.now()));

        service.forgot(disabled.email(), IP);

        assertThat(mailer.sent).isEmpty();
        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void refusesToMintALocalPasswordForAnOAuthOnlyAccount() {
        // ADR 009 §4. Minting one would turn "I know this address" into a second way into an
        // account whose owner deliberately has only one.
        User oauthOnly = users.save(AuthTestFakes.user(EMAIL, "aisyah", null));
        identities.save(new UserIdentity(UUID.randomUUID(), oauthOnly.id(),
                AuthProvider.FIREBASE_GOOGLE, "firebase-uid", EMAIL, Instant.now()));

        service.forgot(EMAIL, IP);

        assertThat(mailer.only().subject()).isEqualTo("About your Travel Planner account");
        assertThat(mailer.only().textBody()).contains("FIREBASE_GOOGLE");
        // No token at all, so there is nothing for that mail to leak either.
        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void countsQuotaAgainstBothTheAddressAndTheClientBeforeAnyAccountLookup() {
        service.forgot("nobody@example.com", IP);

        // Two counters per request: one keyed on the submitted address, one on the caller's IP.
        // Both are consumed for an unregistered address, which is what keeps the limit from
        // distinguishing registered addresses from unregistered ones.
        assertThat(limits.recorded).hasSize(2);
        assertThat(limits.recorded).anySatisfy(key ->
                assertThat(key).startsWith("password_forgot:email"));
        assertThat(limits.recorded).anySatisfy(key ->
                assertThat(key).startsWith("password_forgot:ip"));
    }

    @Test
    void rejectsTheFourthRequestForTheSameAddressWithinTheWindow() {
        givenLocalAccount();

        for (int attempt = 0; attempt < 3; attempt++) {
            service.forgot(EMAIL, "198.51.100." + attempt);
        }

        assertThatThrownBy(() -> service.forgot(EMAIL, "198.51.100.9"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void appliesTheAddressLimitToAnUnregisteredEmailExactlyAsToARegisteredOne() {
        for (int attempt = 0; attempt < 3; attempt++) {
            service.forgot("nobody@example.com", "198.51.100." + attempt);
        }

        assertThatThrownBy(() -> service.forgot("nobody@example.com", "198.51.100.9"))
                .isInstanceOf(RateLimitedException.class);
    }

    // -------------------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------------------

    @Test
    void replacesThePasswordRevokesEverySessionAndConfirmsByMail() {
        User account = givenLocalAccount();
        service.forgot(EMAIL, IP);
        String rawToken = tokenFromLastMail();
        mailer.sent.clear();

        service.reset(rawToken, "a-brand-new-password");

        User updated = users.byId.get(account.id());
        assertThat(updated.passwordHash()).isEqualTo("hash:a-brand-new-password");
        // ADR 009 §1 — the whole reason this flow revokes: whoever was signed in before the reset
        // is, in the case it exists for, the attacker.
        assertThat(updated.tokenVersion()).isEqualTo(account.tokenVersion() + 1);
        assertThat(users.revocations).isOne();
        assertThat(mailer.only().subject())
                .isEqualTo("Your Travel Planner password was changed");
    }

    @Test
    void refusesToReuseAResetLink() {
        givenLocalAccount();
        service.forgot(EMAIL, IP);
        String rawToken = tokenFromLastMail();

        service.reset(rawToken, "a-brand-new-password");

        assertThatThrownBy(() -> service.reset(rawToken, "another-new-password"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesAnUnknownToken() {
        assertThatThrownBy(() -> service.reset("never-issued", "a-brand-new-password"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void doesNotBurnTheLinkWhenTheNewPasswordFailsThePolicy() {
        // The recovery flow must not punish the people who need it: a typo should cost a retry,
        // not the one link they have.
        givenLocalAccount();
        service.forgot(EMAIL, IP);
        String rawToken = tokenFromLastMail();

        assertThatThrownBy(() -> service.reset(rawToken, "short"))
                .isInstanceOf(ValidationFailedException.class);

        assertThatCode(() -> service.reset(rawToken, "a-brand-new-password"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAResetForAnAccountDisabledAfterTheLinkWasMailed() {
        User account = givenLocalAccount();
        service.forgot(EMAIL, IP);
        String rawToken = tokenFromLastMail();

        users.save(new User(account.id(), account.username(), account.email(),
                account.passwordHash(), account.emailVerified(), account.role(), false,
                account.tokenVersion(), null, null, account.createdAt(), Instant.now()));

        assertThatThrownBy(() -> service.reset(rawToken, "a-brand-new-password"))
                .isInstanceOf(InvalidTokenException.class);
    }

    private User givenLocalAccount() {
        return users.save(AuthTestFakes.user(EMAIL, "aisyah", "hash:original-password"));
    }

    private String tokenFromLastMail() {
        String body = mailer.last().textBody();
        int start = body.indexOf("token=") + "token=".length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return body.substring(start, end);
    }
}
