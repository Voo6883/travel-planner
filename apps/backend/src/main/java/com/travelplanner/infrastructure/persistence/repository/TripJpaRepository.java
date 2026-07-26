package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.TripEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data access to {@code trip}.
 *
 * <p>Every finder carries {@code userId}. Spring Data would happily derive
 * {@code findById(tripId)}, and {@link org.springframework.data.repository.CrudRepository} already
 * provides one — which is exactly why the adapter above never exposes it. Derived queries here stay
 * scoped so that the shortest thing to type is also the safe thing (PLAN §4.0.2-L).
 */
public interface TripJpaRepository extends JpaRepository<TripEntity, UUID> {

    Optional<TripEntity> findByIdAndUserId(UUID id, UUID userId);

    List<TripEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * @return rows removed — 0 when the trip does not exist or belongs to somebody else
     */
    // A derived delete needs a transaction of its own when no caller supplies one. The default
    // REQUIRED propagation joins the service's transaction when there is one, so this does not
    // create the independent boundary PLAN §4.0.2-H warns about.
    @Transactional
    int deleteByIdAndUserId(UUID id, UUID userId);
}
