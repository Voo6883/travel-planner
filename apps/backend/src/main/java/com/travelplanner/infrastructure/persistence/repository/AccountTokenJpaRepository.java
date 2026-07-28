package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.infrastructure.persistence.entity.AccountTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code account_token} (V8, §4.0.10). */
public interface AccountTokenJpaRepository extends JpaRepository<AccountTokenEntity, UUID> {

    /** The only read path — {@code ux_account_token_hash} makes it an index lookup. */
    Optional<AccountTokenEntity> findByTokenHash(String tokenHash);

    /**
     * <strong>The single-use guarantee.</strong> Conditional on {@code consumedAt IS NULL}, so two
     * simultaneous clicks on the same link race in the database rather than in Java, and exactly
     * one of them updates a row. A find-then-save in the service would let both win.
     *
     * <p>{@code clearAutomatically} because this bypasses the persistence context; without it the
     * entity loaded a moment earlier in the same transaction would still report itself unspent.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountTokenEntity token set token.consumedAt = :consumedAt "
            + "where token.id = :tokenId and token.consumedAt is null")
    int consume(@Param("tokenId") UUID tokenId, @Param("consumedAt") Instant consumedAt);

    /**
     * Invalidates the account's outstanding tokens of one purpose, which runs whenever a fresh link
     * is issued. Without it a mailbox holding three old reset messages would offer three working
     * ways in, the oldest of which has had the most time to leak.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountTokenEntity token set token.consumedAt = :consumedAt "
            + "where token.userId = :userId and token.purpose = :purpose "
            + "and token.consumedAt is null")
    int consumeAllForUser(@Param("userId") UUID userId,
            @Param("purpose") AccountTokenPurpose purpose,
            @Param("consumedAt") Instant consumedAt);

    /** Keeps the table bounded: an expired token can never be redeemed again. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AccountTokenEntity token where token.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
