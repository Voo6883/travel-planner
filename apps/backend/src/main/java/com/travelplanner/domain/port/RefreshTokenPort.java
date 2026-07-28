package com.travelplanner.domain.port;

import com.travelplanner.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link RefreshToken} (ADR 009 §3). Implemented in
 * {@code infrastructure/persistence/}.
 *
 * <p>Lookup is by hash only. There is deliberately no "find the active token for this user": a
 * user legitimately holds one refresh token per device, and a port that assumed otherwise would
 * make signing in on a phone silently sign the same person out on their laptop.
 */
public interface RefreshTokenPort {

    RefreshToken save(RefreshToken token);

    /** The only read path. The raw token is hashed by the caller; this never sees it. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every token this user still holds — logout-all, reuse detection, and the
     * {@code token_version} bumps that tasks 09, 10, and 12 trigger.
     *
     * @return how many rows were revoked, for the security event log
     */
    int revokeAllForUser(UUID userId, Instant revokedAt);
}
