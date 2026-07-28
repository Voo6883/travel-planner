package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.port.RefreshTokenPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Terminates every live session for one account (ADR 009 §1).
 *
 * <p><strong>This is the extension point tasks 09, 10, and 12 call.</strong> Password change,
 * admin password reset, admin disable, account delete, and provider unlink all end here; none of
 * them should touch {@code token_version} directly, because getting the revocation right means
 * doing three things together and any endpoint that reimplements it will eventually do two.
 *
 * <ol>
 *   <li>{@code token_version + 1} — every issued access token now carries a stale {@code tv} and
 *       is rejected by the authentication filter on its next request.
 *   <li>{@code sessions_valid_after = now} — the second, independent check from ADR 009 §1. It
 *       closes the window in which a token minted in the same instant as the bump would still
 *       carry the new version.
 *   <li>Revoke every stored refresh token — otherwise the holder simply refreshes and is issued a
 *       fresh, valid access token seconds later, and the revocation achieved nothing.
 * </ol>
 *
 * <p>Clearing a cookie is not revocation. That distinction is the reason ADR 009 exists.
 */
@Service
@RequiresDatabase
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final UserRepositoryPort users;
    private final RefreshTokenPort refreshTokens;

    public SessionRevocationService(UserRepositoryPort users, RefreshTokenPort refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    /**
     * Runs in its <strong>own</strong> transaction, not the caller's.
     *
     * <p>The reason is the reuse case in ADR 009 §3: detection ends by throwing
     * {@code UnauthorizedException} at the replaying client, and if the revocation shared that
     * transaction the rollback would undo it. The family would be reported as revoked and would
     * keep working — a security control that fails silently in exactly the incident it exists for.
     *
     * <p>{@code @TransactionalWrite} cannot express this: it fixes propagation at the default, and
     * the deadlock retry it also carries is not wanted around a two-statement revocation.
     *
     * @return {@code true} when an account was found and revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean revokeAllSessions(UUID userId, SessionRevocationReason reason) {
        Instant now = Instant.now();
        int updated = users.revokeSessions(userId, now);
        int revokedTokens = refreshTokens.revokeAllForUser(userId, now);

        // A security event, deliberately at WARN and deliberately without the email address:
        // PLAN §4.0.2-J2 keeps PII out of logs, and the user id is what an investigation joins on.
        log.warn("Sessions revoked for user {} — reason={}, refresh_tokens_revoked={}",
                userId, reason, revokedTokens);
        return updated > 0;
    }
}
