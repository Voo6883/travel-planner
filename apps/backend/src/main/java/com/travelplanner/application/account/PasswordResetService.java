package com.travelplanner.application.account;

import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.exception.InvalidTokenException;
import com.travelplanner.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * UC-A07 — forgot password, and the reset that follows it.
 *
 * <h2>The OAuth-only rule</h2>
 *
 * <p>An account created through Google or GitHub has {@code password_hash IS NULL}. ADR 009 §4
 * forbids this flow from minting one: it would convert "I know this person's email address" into a
 * second, weaker way into an account whose owner deliberately has only one. Such an account gets the
 * same {@code 202} and a "sign in with your provider" mail, and <em>no token is issued at all</em>,
 * so there is nothing for that mail to leak either.
 *
 * <h2>Ordering</h2>
 *
 * <p>{@code reset} validates and encodes the new password <em>before</em> spending the token. A
 * password that fails the policy — a typo, a value pasted with a trailing space — must not burn the
 * one link the user has, or the recovery flow punishes exactly the people who need it.
 *
 * <p>Session revocation is not visible in this class: {@link AccountStore#replacePassword} performs
 * the write and the revocation together, so a reset endpoint cannot express one without the other
 * (ADR 009 §1).
 */
@Service
@RequiresDatabase
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final AccountStore accounts;
    private final AccountTokenService tokens;
    private final AccountLifecycleMailer mailer;

    public PasswordResetService(AccountStore accounts, AccountTokenService tokens,
            AccountLifecycleMailer mailer) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mailer = mailer;
    }

    /**
     * UC-A07 step one. Returns nothing and reveals nothing: the outcome is identical for a
     * registered address, an unregistered one, a disabled account, and a provider-only account
     * (ADR 009 §6).
     *
     * @throws com.travelplanner.domain.exception.RateLimitedException before any lookup, so the
     *         limit cannot tell a registered address from an unregistered one
     */
    public void forgot(String email, String clientIp) {
        mailer.rateLimit(MailRateLimitScope.PASSWORD_FORGOT, email, clientIp);

        Optional<User> account = accounts.liveByEmail(email);
        if (account.isEmpty()) {
            log.info("password_forgot ignored — no live account for the submitted address");
            return;
        }
        sendFor(account.get());
    }

    /**
     * UC-A07 step two. Replaces the password, terminates every session, and confirms by mail.
     *
     * @throws InvalidTokenException for an unknown, expired, spent, or wrong-purpose token, and for
     *         a token whose account has since been disabled or deleted
     * @throws com.travelplanner.domain.exception.ValidationFailedException if the new password fails
     *         the policy — raised before the token is spent
     */
    public void reset(String rawToken, String newPassword) {
        UUID userId = redeemAfterValidating(rawToken, newPassword);
        User account = accounts.liveById(userId).orElseThrow(InvalidTokenException::new);

        accounts.replacePassword(account, newPassword, SessionRevocationReason.PASSWORD_CHANGED);
        mailer.passwordChanged(account.email());
        log.info("password_reset_completed user={}", userId);
    }

    /**
     * Checks the new password against the policy first, so a rejected password leaves the link
     * usable. {@code AccountStore.replacePassword} runs the same check again before writing —
     * cheap, and the only place the check is load-bearing.
     */
    private UUID redeemAfterValidating(String rawToken, String newPassword) {
        accounts.validatePassword(newPassword);
        return tokens.redeem(rawToken, AccountTokenPurpose.PASSWORD_RESET);
    }

    private void sendFor(User account) {
        if (account.isOAuthOnly()) {
            mailer.providerSignIn(account);
            return;
        }
        mailer.passwordReset(account.email(),
                tokens.issue(account.id(), AccountTokenPurpose.PASSWORD_RESET));
    }
}
