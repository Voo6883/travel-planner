package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.auth.AuthTestFakes.FakeHasher;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.AccountDisabledException;
import com.travelplanner.domain.exception.AccountLockedException;
import com.travelplanner.domain.exception.EmailNotVerifiedException;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.LoginAttemptPort;
import com.travelplanner.domain.port.PasswordHasherPort;
import com.travelplanner.infrastructure.auth.local.LocalPasswordIdentityAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * UC-A04, plus the parts of ADR 009 that decide <em>whether</em> a correct password is enough.
 *
 * <p>Runs the real {@link LocalPasswordIdentityAdapter} rather than a stub, because the property
 * most worth protecting here is that an unknown identifier and a wrong password are
 * indistinguishable — and that only holds if the thing actually deciding it is under test.
 */
class AuthenticationServiceTest {

    private static final String IP = "203.0.113.7";

    private final FakeUsers users = new FakeUsers();
    private final PasswordHasherPort hasher = new FakeHasher();
    private final CountingAttempts attempts = new CountingAttempts();
    private final AuthenticationService authentication = new AuthenticationService(
            new IdentityProviderRegistry(List.of(new LocalPasswordIdentityAdapter(users, hasher))),
            users,
            new LoginAttemptGuard(attempts, new AuthSecurityProperties()));

    @Test
    void signsInByEmail() {
        User stored = givenVerifiedUser("aisyah@example.com", "aisyah");

        User authenticated = authentication.authenticate(
                new LoginCommand("aisyah@example.com", "correct-password"), IP);

        assertThat(authenticated.id()).isEqualTo(stored.id());
    }

    @Test
    void signsInByUsernameCaseInsensitively() {
        User stored = givenVerifiedUser("aisyah@example.com", "aisyah");

        User authenticated =
                authentication.authenticate(new LoginCommand("AISYAH", "correct-password"), IP);

        assertThat(authenticated.id()).isEqualTo(stored.id());
    }

    @Test
    void rejectsAWrongPassword() {
        givenVerifiedUser("aisyah@example.com", "aisyah");

        assertThatThrownBy(() ->
                authentication.authenticate(new LoginCommand("aisyah", "wrong-password"), IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reportsAnUnknownAccountExactlyAsItReportsAWrongPassword() {
        givenVerifiedUser("aisyah@example.com", "aisyah");

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(() ->
                authentication.authenticate(new LoginCommand("nobody@example.com", "x-password"), IP));
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(() ->
                authentication.authenticate(new LoginCommand("aisyah", "wrong-password"), IP));

        // Same type, same code, same message, no details — otherwise the endpoint enumerates
        // accounts for anyone with a password they know is wrong (ADR 009 §6).
        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(((InvalidCredentialsException) unknown).details())
                .isEqualTo(((InvalidCredentialsException) wrongPassword).details())
                .isEmpty();
    }

    @Test
    void refusesAnUnverifiedAccountEvenWithTheCorrectPassword() {
        // UC-A08: local sign-up cannot reach the planner until the address is confirmed.
        givenUser("aisyah@example.com", "aisyah", false, true);

        assertThatThrownBy(() ->
                authentication.authenticate(new LoginCommand("aisyah", "correct-password"), IP))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void refusesADisabledAccountEvenWithTheCorrectPassword() {
        givenUser("aisyah@example.com", "aisyah", true, false);

        assertThatThrownBy(() ->
                authentication.authenticate(new LoginCommand("aisyah", "correct-password"), IP))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void raisesTheDistinguishableFailuresOnlyAfterThePasswordHasVerified() {
        // An unverified account must not be discoverable by someone who does not know the
        // password, or email_not_verified becomes the enumeration channel invalid_credentials
        // was carefully made not to be.
        givenUser("aisyah@example.com", "aisyah", false, true);

        assertThatThrownBy(() ->
                authentication.authenticate(new LoginCommand("aisyah", "wrong-password"), IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void countsAFailedAttemptButNotASuccessfulOne() {
        givenVerifiedUser("aisyah@example.com", "aisyah");

        org.assertj.core.api.Assertions.catchThrowable(() ->
                authentication.authenticate(new LoginCommand("aisyah", "wrong-password"), IP));
        assertThat(attempts.recorded).containsExactly("aisyah|" + IP);

        authentication.authenticate(new LoginCommand("aisyah", "correct-password"), IP);
        assertThat(attempts.cleared).containsExactly("aisyah|" + IP);
    }

    @Test
    void countsFailuresForIdentifiersThatWereNeverRegistered() {
        // Skipping them would make the lockout itself an existence oracle: registered addresses
        // could be told apart by which ones can be locked.
        org.assertj.core.api.Assertions.catchThrowable(() ->
                authentication.authenticate(new LoginCommand("nobody@example.com", "x-password"), IP));

        assertThat(attempts.recorded).containsExactly("nobody@example.com|" + IP);
    }

    @Test
    void checksTheLockoutBeforeThePasswordSoALockedKeyCannotWearItDown() {
        givenVerifiedUser("aisyah@example.com", "aisyah");
        attempts.failures = 5;

        assertThatThrownBy(() ->
                authentication.authenticate(new LoginCommand("aisyah", "correct-password"), IP))
                .isInstanceOf(AccountLockedException.class);
        assertThat(attempts.recorded).isEmpty();
    }

    private User givenVerifiedUser(String email, String username) {
        return givenUser(email, username, true, true);
    }

    private User givenUser(String email, String username, boolean verified, boolean enabled) {
        Instant now = Instant.now();
        User user = new User(UUID.randomUUID(), username, email, hasher.hash("correct-password"),
                verified, Role.USER, enabled, 0, null, null, now, now);
        return users.save(user);
    }

    private static final class CountingAttempts implements LoginAttemptPort {

        private final List<String> recorded = new ArrayList<>();
        private final List<String> cleared = new ArrayList<>();
        private int failures;

        @Override
        public void recordFailure(String identifier, String clientIp) {
            recorded.add(identifier + "|" + clientIp);
        }

        @Override
        public int countFailuresSince(LoginAttemptKey key, Instant since) {
            return failures;
        }

        @Override
        public void clearFailures(String identifier, String clientIp) {
            cleared.add(identifier + "|" + clientIp);
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }
    }
}
