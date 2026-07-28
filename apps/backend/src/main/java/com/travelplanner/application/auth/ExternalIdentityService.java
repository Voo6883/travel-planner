package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The entry point for every provider that is not {@code LOCAL} (UC-A02, UC-A03, UC-A05, UC-A06,
 * UC-A09).
 *
 * <p>Three steps, in an order that is load-bearing:
 *
 * <ol>
 *   <li><b>Verify, outside any transaction.</b> The adapter talks to Google or GitHub over HTTPS.
 *       {@code AGENTS.md} forbids external HTTP inside {@code @Transactional} — a provider that
 *       takes ten seconds to answer would hold a pooled database connection for ten seconds, and a
 *       retried transaction would repeat the call.
 *   <li><b>Resolve, inside one.</b> {@link AccountLinkingService} applies ADR 009 §4 and writes.
 *   <li><b>Issue the session.</b> Through {@link SessionService}, the same one local sign-in uses,
 *       so all three providers converge on one session model (PLAN §4.0.5).
 * </ol>
 *
 * <p>Not itself transactional, for the reason above and for {@link AuthService}'s: a transaction
 * spanning a whole sign-in would roll back on every failure, including the ones worth recording.
 *
 * <p>Adding a provider does not change this class. {@link IdentityProviderRegistry} routes on the
 * enum, so a new adapter is a new bean — the extension seam task 08 built and this task is the
 * first to use.
 */
@Service
@RequiresDatabase
public class ExternalIdentityService {

    private static final Logger log = LoggerFactory.getLogger(ExternalIdentityService.class);

    private final IdentityProviderRegistry providers;
    private final AccountLinkingService linking;
    private final SessionService sessions;

    public ExternalIdentityService(IdentityProviderRegistry providers, AccountLinkingService linking,
            SessionService sessions) {
        this.providers = providers;
        this.linking = linking;
        this.sessions = sessions;
    }

    /** Sign-up and sign-in are the same call; which one it was comes back in the result. */
    public ExternalSignIn signIn(AuthProvider provider, ProviderCredential credential) {
        IdentityClaims claims = verify(provider, credential);
        LinkedAccount account = resolve(claims);
        return ExternalSignIn.of(sessions.issueFor(account.user()), account);
    }

    /**
     * The explicit link confirmation (UC-A09). The caller is identified by their session, never by
     * anything in the request — a body-supplied user id would be a caller asserting whose account
     * gains a new way in.
     */
    public void link(UUID callerId, AuthProvider provider, ProviderCredential credential) {
        linking.link(callerId, verify(provider, credential));
    }

    private IdentityClaims verify(AuthProvider provider, ProviderCredential credential) {
        return providers.forProvider(provider).authenticate(credential);
    }

    /**
     * Retries once on a unique-index collision.
     *
     * <p>Two tabs, one first-ever Google sign-in: both find no identity, both insert, and one loses
     * to {@code ux_user_identity_provider_subject} or {@code ux_user_email_lower}. The loser's work
     * is already done by the winner, so a second resolution finds the row and signs in — whereas
     * letting the violation escape would turn an ordinary double-click into a 500.
     *
     * <p>The retry is here rather than inside {@link AccountLinkingService} because a transaction
     * that has hit a constraint violation is doomed: it has to be re-entered, not resumed.
     */
    private LinkedAccount resolve(IdentityClaims claims) {
        try {
            return linking.resolveSignIn(claims);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.info("Concurrent first sign-in for {} — re-resolving after a unique-index rejection",
                    claims.provider());
            return linking.resolveSignIn(claims);
        }
    }
}
