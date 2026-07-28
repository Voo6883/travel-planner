package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.LoginAttemptEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code login_attempt} (V7, ADR 009 §6). */
public interface LoginAttemptJpaRepository extends JpaRepository<LoginAttemptEntity, UUID> {

    /**
     * The lockout read. Counting in the database rather than loading rows keeps the work
     * proportional to the answer, and {@code ix_login_attempt_key} covers all three predicates.
     */
    @Query("select count(attempt) from LoginAttemptEntity attempt "
            + "where attempt.loginIdentifier = :identifier and attempt.clientIp = :clientIp "
            + "and attempt.attemptedAt >= :since")
    long countFailures(@Param("identifier") String identifier, @Param("clientIp") String clientIp,
            @Param("since") Instant since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginAttemptEntity attempt "
            + "where attempt.loginIdentifier = :identifier and attempt.clientIp = :clientIp")
    int deleteForKey(@Param("identifier") String identifier, @Param("clientIp") String clientIp);

    /** Keeps the table a window: rows past the lockout period can never affect a decision. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginAttemptEntity attempt where attempt.attemptedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
