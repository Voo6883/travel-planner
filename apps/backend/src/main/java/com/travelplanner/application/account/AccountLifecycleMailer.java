package com.travelplanner.application.account;

import com.travelplanner.application.mail.AccountMailService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Everything the account-lifecycle services need in order to send a mail, behind one collaborator.
 *
 * <p><strong>Why this exists.</strong> Sending an account mail correctly needs three things: the
 * quota check (ADR 009 §6), the composition and after-commit dispatch
 * ({@link AccountMailService}), and — for the provider-only case — the account's linked providers.
 * Injecting all three into every lifecycle service would put five constructor arguments on each of
 * them, past the three {@code AGENTS.md} allows, and would repeat the same wiring four times.
 *
 * <p>It is a gateway, not a policy: nothing here decides <em>whether</em> an account should be
 * mailed. That decision belongs to the service that knows what happened to the account.
 */
@Service
@RequiresDatabase
public class AccountLifecycleMailer {

    private final AccountMailService mail;
    private final MailRateLimiter rateLimiter;
    private final UserIdentityRepositoryPort identities;

    public AccountLifecycleMailer(AccountMailService mail, MailRateLimiter rateLimiter,
            UserIdentityRepositoryPort identities) {
        this.mail = mail;
        this.rateLimiter = rateLimiter;
        this.identities = identities;
    }

    /**
     * Consumes quota for one mail action, before any account lookup happens.
     *
     * <p>The ordering is the enumeration defence: a limit applied after "does this account exist?"
     * would let a caller tell registered from unregistered addresses by which ones can exhaust it.
     *
     * @throws com.travelplanner.domain.exception.RateLimitedException when the window is spent
     */
    public void rateLimit(MailRateLimitScope scope, String email, String clientIp) {
        rateLimiter.checkAndRecord(scope, email, clientIp);
    }

    /** UC-N01. */
    public void welcome(String email, String username) {
        mail.sendWelcome(email, username);
    }

    /** UC-N02, UC-A08, UC-A13. The raw token goes into a link and nowhere else. */
    public void verification(String email, String rawToken) {
        mail.sendVerification(email, rawToken);
    }

    /** UC-N03, UC-A07. */
    public void passwordReset(String email, String rawToken) {
        mail.sendPasswordReset(email, rawToken);
    }

    /** After a reset or a self-service change — the account owner's only alarm signal. */
    public void passwordChanged(String email) {
        mail.sendPasswordChanged(email);
    }

    /** The address is already registered with a local password (ADR 009 §6's deferred answer). */
    public void accountAlreadyExists(String email) {
        mail.sendAccountAlreadyExists(email);
    }

    /** The address was free but the username was not, so no account was created. */
    public void usernameTaken(String email, String username) {
        mail.sendUsernameTaken(email, username);
    }

    /**
     * The account signs in only through a provider (ADR 009 §4). Resolves the provider names here so
     * that callers do not each need the identity repository to write one sentence of a mail.
     */
    public void providerSignIn(User account) {
        mail.sendProviderSignIn(account.email(), providerNamesFor(account.id()));
    }

    private List<String> providerNamesFor(UUID userId) {
        return identities.findAllByUserId(userId).stream()
                .map(UserIdentity::provider)
                .map(Enum::name)
                .sorted()
                .toList();
    }
}
