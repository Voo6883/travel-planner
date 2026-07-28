package com.travelplanner.application.account;

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
 * UC-A08 (confirm the address) and UC-A13 (send the link again).
 *
 * <p><strong>Not transactional at this level, deliberately.</strong> Each collaborator declares its
 * own boundary, matching {@code AuthService}. Two things depend on that: the rate-limit counter must
 * survive the {@code RateLimitedException} that follows it, and mail dispatched from here must find
 * the account write already committed rather than pending.
 */
@Service
@RequiresDatabase
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final AccountStore accounts;
    private final AccountTokenService tokens;
    private final AccountLifecycleMailer mailer;

    public EmailVerificationService(AccountStore accounts, AccountTokenService tokens,
            AccountLifecycleMailer mailer) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mailer = mailer;
    }

    /**
     * UC-A13 — send the verification mail again.
     *
     * <p>Returns nothing, and never signals what it found. The response is the same {@code 202} for
     * a registered address, an unregistered one, an already-verified account, and a provider-only
     * account (ADR 009 §6). What differs is which mail goes out, and that is visible only to
     * whoever controls the mailbox.
     *
     * <p>An unregistered address gets <em>no</em> mail. "You have no account here", sent to any
     * address on request, would make this endpoint a way to mail strangers on demand.
     *
     * @throws com.travelplanner.domain.exception.RateLimitedException before any lookup happens, so
     *         the limit cannot distinguish a registered address from an unregistered one
     */
    public void resend(String email, String clientIp) {
        mailer.rateLimit(MailRateLimitScope.VERIFY_EMAIL_RESEND, email, clientIp);

        Optional<User> account = accounts.liveByEmail(email);
        if (account.isEmpty()) {
            log.info("verify_email_resend ignored — no live account for the submitted address");
            return;
        }
        sendFor(account.get());
    }

    /**
     * UC-A08 — the mailed link was followed.
     *
     * @throws InvalidTokenException for an unknown, expired, spent, or wrong-purpose token, and for
     *         a token whose account has since been disabled or deleted
     */
    public void confirm(String rawToken) {
        UUID userId = tokens.redeem(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION);
        User account = accounts.liveById(userId).orElseThrow(InvalidTokenException::new);

        if (account.emailVerified()) {
            // Idempotent. The token is spent either way, and a second confirmation is a duplicate
            // click — or a mail scanner following the link — rather than an error worth showing.
            return;
        }
        accounts.markEmailVerified(account);
    }

    /** Which mail an existing account gets. Never "nothing" — that is the dead end this task closes. */
    private void sendFor(User account) {
        if (account.emailVerified()) {
            // Already usable. Pointing at sign-in and forgot-password is the honest answer, and it
            // is the same mail a duplicate sign-up gets: the recipient's situation is identical.
            mailer.accountAlreadyExists(account.email());
            return;
        }
        if (account.isOAuthOnly()) {
            // Unverified and password-less should not arise — a provider asserts the address at
            // creation — but if it ever does, a verification link is the wrong remedy and "sign in
            // with your provider" is the right one (ADR 009 §4).
            mailer.providerSignIn(account);
            return;
        }
        mailer.verification(account.email(),
                tokens.issue(account.id(), AccountTokenPurpose.EMAIL_VERIFICATION));
    }
}
