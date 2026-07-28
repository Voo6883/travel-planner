package com.travelplanner.domain.model;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One issued refresh token (ADR 009 §3, table {@code refresh_token} from V4).
 *
 * <p><strong>Only the hash lives here.</strong> The raw token exists in the httpOnly cookie and
 * nowhere else, so a leaked database dump hands out no live sessions.
 *
 * <p>The two nullable instants are the whole rotation protocol:
 *
 * <ul>
 *   <li>{@code rotatedAt} — this token was already exchanged for a successor. Presenting it again
 *       is the reuse signal that revokes the entire family.
 *   <li>{@code revokedAt} — this token was invalidated explicitly (logout, logout-all, or the
 *       reuse response above).
 * </ul>
 *
 * <p>A token is usable only while both are {@code null} and {@code expiresAt} is in the future.
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant rotatedAt,
        Instant revokedAt,
        Instant createdAt) {

    /** SHA-256 hex digest width, matching {@code refresh_token.token_hash char(64)}. */
    public static final int HASH_LENGTH = 64;

    public RefreshToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        tokenHash = requireHash(tokenHash);
    }

    /** A freshly minted token: not yet rotated, not yet revoked. */
    public static RefreshToken issued(UUID userId, String tokenHash, Instant expiresAt) {
        Instant now = Instant.now();
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt, null, null, now);
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    /**
     * Already exchanged or already invalidated. Both conditions mean the same thing to the refresh
     * endpoint — the caller is presenting a token that must no longer work — but only
     * {@link #isRotated()} implies somebody replayed a superseded token.
     */
    public boolean isSpent() {
        return rotatedAt != null || revokedAt != null;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public boolean isUsableAt(Instant instant) {
        return !isSpent() && !isExpiredAt(instant);
    }

    public RefreshToken rotatedAt(Instant instant) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, instant, revokedAt, createdAt);
    }

    public RefreshToken revokedAt(Instant instant) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, rotatedAt, instant, createdAt);
    }

    private static String requireHash(String tokenHash) {
        if (tokenHash == null || tokenHash.length() != HASH_LENGTH) {
            throw ValidationFailedException.field("token_hash",
                    "must be a " + HASH_LENGTH + "-character digest");
        }
        return tokenHash;
    }
}
