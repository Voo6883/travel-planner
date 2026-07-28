package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.AiCallLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code ai_call_log} (V11).
 *
 * <p>Insert only. Reads are dashboards and ad-hoc SQL, not application code — an application that
 * queries its own metrics table is usually one that has started making decisions on them, which is
 * how a cost log turns into a rate limiter nobody designed.
 */
public interface AiCallLogJpaRepository extends JpaRepository<AiCallLogEntity, UUID> {
}
