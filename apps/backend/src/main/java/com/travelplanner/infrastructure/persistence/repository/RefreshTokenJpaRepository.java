package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code refresh_token} (ADR 009 §3). */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /** The only read path — {@code ux_refresh_token_hash} makes it an index lookup. */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Bulk revocation for logout-all, reuse detection, and every {@code token_version} bump.
     *
     * <p>A single {@code UPDATE} rather than load-modify-save: a user with many devices would
     * otherwise mean one round trip per token, and the operation has to be atomic — a partial
     * revocation leaves exactly the sessions an incident response was trying to close.
     *
     * <p>{@code clearAutomatically} because this statement bypasses the persistence context; a
     * stale entity cached from earlier in the transaction would still report itself unrevoked.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenEntity token set token.revokedAt = :revokedAt "
            + "where token.userId = :userId and token.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * The atomic half of rotation (ADR 009 §3).
     *
     * <p>Every precondition the service would otherwise have checked in Java is in the
     * {@code WHERE} clause, so PostgreSQL — not the application — decides which of two simultaneous
     * refreshes gets to rotate this token. Under {@code READ COMMITTED} the second statement blocks
     * on the row lock, then re-evaluates its predicate against the committed row, sees a non-null
     * {@code rotated_at}, and reports zero rows affected. That count is the whole answer: one caller
     * gets {@code 1} and mints a session, everyone else gets {@code 0} and gets nothing.
     *
     * <p>A conditional {@code UPDATE} rather than {@code PESSIMISTIC_WRITE} plus a save. Both are
     * correct, but this one is a single round trip, needs no lock timeout tuning, and cannot be
     * accidentally weakened later by a refactor that moves the check outside the lock — there is no
     * check outside the statement to move.
     *
     * @return {@code 1} when this call claimed the token, {@code 0} when there was nothing to claim
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenEntity token set token.rotatedAt = :rotatedAt "
            + "where token.tokenHash = :tokenHash and token.rotatedAt is null "
            + "and token.revokedAt is null and token.expiresAt > :rotatedAt")
    int markRotated(@Param("tokenHash") String tokenHash, @Param("rotatedAt") Instant rotatedAt);
}
