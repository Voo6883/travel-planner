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
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.InvalidTokenException;
import com.travelplanner.domain.exception.RateLimitedException;
import com.travelplanner.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-A08 (confirm) and UC-A13 (resend), with ADR 009 §6's uniformity as the property under test. */
class EmailVerificationServiceTest {

    private static final String EMAIL = "aisyah@example.com";
    private static final String IP = "203.0.113.7";

    private final FakeUsers users = new FakeUsers();
    private final FakeAccountTokens tokens = new FakeAccountTokens();
    private final FakeMailRateLimits limits = new FakeMailRateLimits();
    private final CapturingMailer mailer = new CapturingMailer();

    private final AccountTokenService tokenService = AccountTestFakes.tokenService(tokens);
    private final EmailVerificationService service = new EmailVerificationService(
            AccountTestFakes.accountStore(users), tokenService,
            AccountTestFakes.lifecycleMailer(mailer, limits, new FakeIdentities()));

    // -------------------------------------------------------------------------------------
    // UC-A08 — confirm
    // -------------------------------------------------------------------------------------

    @Test
    void flipsEmailVerifiedWhenTheMailedLinkIsFollowed() {
        User account = givenUnverifiedAccount();
        String rawToken = tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);

        service.confirm(rawToken);

        assertThat(users.byId.get(account.id()).emailVerified()).isTrue();
        // No revocation: nothing about any session changed.
        assertThat(users.revocations).isZero();
    }

    @Test
    void acceptsTheLinkOnlyOnce() {
        User account = givenUnverifiedAccount();
        String rawToken = tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);
        service.confirm(rawToken);

        assertThatThrownBy(() -> service.confirm(rawToken)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void reportsUnknownExpiredAndMalformedTokensIdentically() {
        assertThatThrownBy(() -> service.confirm("never-issued"))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.confirm(""))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.confirm(null))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesALinkWhoseAccountWasDisabledAfterItWasMailed() {
        User account = givenUnverifiedAccount();
        String rawToken = tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);
        users.save(new User(account.id(), account.username(), account.email(),
                account.passwordHash(), false, Role.USER, false, 0, null, null,
                account.createdAt(), Instant.now()));

        assertThatThrownBy(() -> service.confirm(rawToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void isIdempotentForAnAccountThatIsAlreadyVerified() {
        User account = users.save(AuthTestFakes.user(EMAIL, "aisyah", "hash:pw"));
        String rawToken = tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);

        // A mail scanner following the link before the user does must not turn into an error the
        // user then sees.
        assertThatCode(() -> service.confirm(rawToken)).doesNotThrowAnyException();
        assertThat(users.byId.get(account.id()).emailVerified()).isTrue();
    }

    // -------------------------------------------------------------------------------------
    // UC-A13 — resend
    // -------------------------------------------------------------------------------------

    @Test
    void mailsAFreshLinkToAnUnverifiedAccount() {
        User account = givenUnverifiedAccount();

        service.resend(EMAIL, IP);

        assertThat(mailer.only().subject()).isEqualTo("Confirm your email address");
        assertThat(tokens.liveFor(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION)).isPresent();
    }

    @Test
    void invalidatesThePreviousLinkWhenANewOneIsSent() {
        User account = givenUnverifiedAccount();
        String first = tokenService.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION);

        service.resend(EMAIL, IP);

        assertThatThrownBy(() -> service.confirm(first)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void staysSilentAndMailsNothingForAnUnregisteredAddress() {
        assertThatCode(() -> service.resend("nobody@example.com", IP)).doesNotThrowAnyException();

        assertThat(mailer.sent).isEmpty();
        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void pointsAnAlreadyVerifiedAccountAtSignInRatherThanMintingAnotherLink() {
        users.save(AuthTestFakes.user(EMAIL, "aisyah", "hash:pw"));

        service.resend(EMAIL, IP);

        assertThat(mailer.only().subject()).isEqualTo("About your Travel Planner sign-up");
        assertThat(tokens.byId).isEmpty();
    }

    @Test
    void countsQuotaBeforeLookingTheAccountUpAtAll() {
        service.resend("nobody@example.com", IP);

        assertThat(limits.recorded).hasSize(2);
        assertThat(limits.recorded).anySatisfy(key ->
                assertThat(key).startsWith("verify_email_resend:email"));
        assertThat(limits.recorded).anySatisfy(key ->
                assertThat(key).startsWith("verify_email_resend:ip"));
    }

    @Test
    void rejectsTheFourthResendForTheSameAddressWithinTheWindow() {
        givenUnverifiedAccount();

        for (int attempt = 0; attempt < 3; attempt++) {
            service.resend(EMAIL, "198.51.100." + attempt);
        }

        assertThatThrownBy(() -> service.resend(EMAIL, "198.51.100.9"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void rejectsTheEleventhResendFromOneAddressEvenAcrossDifferentEmails() {
        // The per-IP ceiling is looser than the per-email one, because a university or a carrier
        // NAT puts many legitimate users behind one address — but it still exists, or one machine
        // could walk a list of a million addresses at full speed.
        for (int attempt = 0; attempt < 10; attempt++) {
            service.resend("nobody" + attempt + "@example.com", IP);
        }

        assertThatThrownBy(() -> service.resend("someone-else@example.com", IP))
                .isInstanceOf(RateLimitedException.class);
    }

    private User givenUnverifiedAccount() {
        return users.save(new User(UUID.randomUUID(), "aisyah", EMAIL, "hash:pw", false,
                Role.USER, true, 0, null, null, Instant.now(), Instant.now()));
    }
}
