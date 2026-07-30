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
     * Claims one token for rotation, atomically.
     *
     * <p>A read-check-write rotation is not safe here. Two refreshes arriving together both read a
     * token whose {@code rotated_at} is still null, both decide it is usable, and both write — and
     * the account ends up with two live refresh tokens descending from one. That is the exact state
     * reuse detection exists to make impossible, so the transition has to be decided by the
     * database and not by the caller: a single conditional {@code UPDATE} whose {@code WHERE} clause
     * carries every precondition, and whose row count says whether this caller was the one that won.
     *
     * @return {@code true} when this call moved the token from unspent-and-unexpired to rotated;
     *     {@code false} when there was nothing to claim — unknown hash, already rotated, revoked,
     *     expired, or another transaction claimed it first
     */
    boolean markRotated(String tokenHash, Instant rotatedAt);

    /**
     * Revokes every token this user still holds — logout-all, reuse detection, and the
     * {@code token_version} bumps that tasks 09, 10, and 12 trigger.
     *
     * @return how many rows were revoked, for the security event log
     */
    int revokeAllForUser(UUID userId, Instant revokedAt);
}
