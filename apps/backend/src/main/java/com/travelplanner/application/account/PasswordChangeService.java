package com.travelplanner.application.account;

import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * UC-A12 — a signed-in user changes their own password.
 *
 * <h2>Why the current password is required</h2>
 *
 * <p>The session cookie proves the browser was signed in; it does not prove the person at the
 * keyboard is the account owner. Without this check, an unlocked laptop, a shared machine with a
 * remembered session, or a stolen cookie becomes a permanent account takeover in one request —
 * because changing the password also revokes every other session, including the real owner's.
 *
 * <p>A wrong current password is {@code invalid_credentials}, the same code sign-in returns. That
 * also covers the OAuth-only case for free: an account with no stored hash can never match, and
 * {@code PasswordPolicy.matches} takes comparable time either way, so the response does not reveal
 * whether the account has a local password at all.
 *
 * <h2>Every session ends, including this one</h2>
 *
 * <p>That is the point of the feature rather than a side effect. A user who changes their password
 * because they believe they were compromised is telling the system to evict everyone; leaving the
 * attacker's session alive would make the action theatre. The controller therefore clears the
 * caller's cookies, and the client signs in again.
 *
 * <p>The revocation is performed by {@link AccountStore#replacePassword} together with the write,
 * so this endpoint cannot express one without the other (ADR 009 §1).
 */
@Service
@RequiresDatabase
public class PasswordChangeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final AccountStore accounts;
    private final AccountLifecycleMailer mailer;

    public PasswordChangeService(AccountStore accounts, AccountLifecycleMailer mailer) {
        this.accounts = accounts;
        this.mailer = mailer;
    }

    /**
     * @param userId taken from the session, never from the request body — a body-supplied id would
     *        be a caller asserting whose password they are changing
     * @throws UnauthorizedException if the account has been disabled or deleted since the session
     *         was issued
     * @throws InvalidCredentialsException if the current password does not verify
     * @throws ValidationFailedException if the new password fails the policy, or repeats the current
     *         one
     */
    public void change(UUID userId, String currentPassword, String newPassword) {
        User account = accounts.liveById(userId).orElseThrow(UnauthorizedException::new);

        if (!accounts.passwordMatches(account, currentPassword)) {
            throw new InvalidCredentialsException();
        }
        if (accounts.passwordMatches(account, newPassword)) {
            // Not pedantry: a "change" that changes nothing still revokes every session and sends
            // an alarming confirmation mail, so silently accepting it produces a security alert for
            // an event that did not happen.
            throw ValidationFailedException.field("new_password",
                    "must differ from the current password");
        }

        accounts.replacePassword(account, newPassword, SessionRevocationReason.PASSWORD_CHANGED);
        mailer.passwordChanged(account.email());
        log.info("password_changed user={}", userId);
    }
}
