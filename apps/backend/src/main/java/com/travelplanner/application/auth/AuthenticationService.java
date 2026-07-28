package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.AccountDisabledException;
import com.travelplanner.domain.exception.EmailNotVerifiedException;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.IdentityClaims;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Turns a presented credential into an account that is allowed to sign in (UC-A04).
 *
 * <p>Order is the security property here:
 *
 * <ol>
 *   <li><b>Lockout first.</b> A locked key never reaches the password comparison, so the lockout
 *       cannot be worn down by the very attempts it is counting.
 *   <li><b>Credential next</b>, through {@link com.travelplanner.domain.port.IdentityProviderPort}
 *       — one uniform failure for an unknown account and for a wrong password alike.
 *   <li><b>Account state last.</b> {@code account_disabled} and {@code email_not_verified} are
 *       distinguishable codes, so they are raised only <em>after</em> the password has verified.
 *       Checking them earlier would let anyone enumerate which addresses are registered but
 *       unconfirmed, without knowing a single password.
 * </ol>
 *
 * <p>Not transactional. The failure counter must survive the exception that follows it, and a
 * transaction spanning the whole method would roll the counter back on every rejected attempt —
 * leaving a lockout that never locks.
 */
@Service
@RequiresDatabase
public class AuthenticationService {

    private final IdentityProviderRegistry providers;
    private final UserRepositoryPort users;
    private final LoginAttemptGuard attempts;

    public AuthenticationService(IdentityProviderRegistry providers, UserRepositoryPort users,
            LoginAttemptGuard attempts) {
        this.providers = providers;
        this.users = users;
        this.attempts = attempts;
    }

    /**
     * @param clientIp the caller's address, half of the ADR 009 §6 lockout key
     * @throws com.travelplanner.domain.exception.AccountLockedException when the window is spent
     * @throws InvalidCredentialsException when the credential does not verify
     */
    public User authenticate(LoginCommand command, String clientIp) {
        attempts.checkNotLocked(command.identifier(), clientIp);

        IdentityClaims claims = verify(command, clientIp);
        attempts.recordSuccess(command.identifier(), clientIp);

        User user = users.findById(subjectOf(claims)).orElseThrow(InvalidCredentialsException::new);
        if (!user.enabled()) {
            throw new AccountDisabledException();
        }
        if (!user.emailVerified()) {
            // UC-A08: local sign-up cannot reach the planner until the address is confirmed, and
            // the login error state is where the frontend offers to resend the mail.
            throw new EmailNotVerifiedException();
        }
        return user;
    }

    private IdentityClaims verify(LoginCommand command, String clientIp) {
        try {
            return providers.forProvider(AuthProvider.LOCAL).authenticate(command.toCredential());
        } catch (InvalidCredentialsException rejected) {
            attempts.recordFailure(command.identifier(), clientIp);
            throw rejected;
        }
    }

    /** For {@code LOCAL} the provider subject is this system's own user id. */
    private static UUID subjectOf(IdentityClaims claims) {
        try {
            return UUID.fromString(claims.subject());
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCredentialsException();
        }
    }
}
