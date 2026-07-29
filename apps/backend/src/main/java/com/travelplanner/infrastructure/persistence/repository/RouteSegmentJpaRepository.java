package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.RouteSegmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code route_segment} (V16).
 *
 * <p>{@code join fetch s.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned.
 *
 * <p>The three foreign keys to areas and modes stay raw uuids on the entity, so this query joins
 * only the source. C3 already holds the areas it asked about; re-fetching them per segment would
 * multiply the row count for data the caller has.
 */
public interface RouteSegmentJpaRepository extends JpaRepository<RouteSegmentEntity, UUID> {

    @Query("""
            select s from RouteSegmentEntity s
            join fetch s.source
            where s.destinationId = :destinationId
            """)
    List<RouteSegmentEntity> findByDestinationId(@Param("destinationId") UUID destinationId);
}
