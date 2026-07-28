package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.MailRateLimitEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code mail_rate_limit} (V9, ADR 009 §6). */
public interface MailRateLimitJpaRepository extends JpaRepository<MailRateLimitEntity, UUID> {

    /**
     * The only read. Counting in the database rather than loading rows keeps the work proportional
     * to the answer, and {@code ix_mail_rate_limit_key} covers all three predicates.
     */
    @Query("select count(hit) from MailRateLimitEntity hit "
            + "where hit.scope = :scope and hit.subjectHash = :subjectHash "
            + "and hit.requestedAt >= :since")
    long countSince(@Param("scope") String scope, @Param("subjectHash") String subjectHash,
            @Param("since") Instant since);

    /** Keeps the table a window rather than an unbounded record of who asked for what. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MailRateLimitEntity hit where hit.requestedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
