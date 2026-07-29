package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.PlannerSessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code planner_session} (V19).
 *
 * <p>Every finder carries {@code userId}, for the reason spelled out in {@link TripJpaRepository}:
 * the shortest thing to type must also be the safe thing (PLAN §4.0.2-L).
 */
public interface PlannerSessionJpaRepository extends JpaRepository<PlannerSessionEntity, UUID> {

    /**
     * The user's resumable planner chat.
     *
     * <p>Returns at most one row because {@code uq_planner_session_user_open} — a unique index
     * partial on {@code ended_at IS NULL} — makes a second open session unrepresentable. Derived
     * rather than written out: {@code findFirstBy…} would compile against the same data while
     * quietly tolerating the duplicate the index exists to prevent.
     */
    Optional<PlannerSessionEntity> findByUserIdAndEndedAtIsNull(UUID userId);

    Optional<PlannerSessionEntity> findByIdAndUserId(UUID id, UUID userId);
}
