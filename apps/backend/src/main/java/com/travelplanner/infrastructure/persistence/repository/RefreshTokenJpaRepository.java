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
}
