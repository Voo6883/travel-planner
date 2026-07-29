package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.DestinationAreaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code destination_area} (V14).
 *
 * <p><strong>{@code join fetch a.source} is an N+1 fix, not a style choice.</strong> {@code source}
 * is {@code LAZY} and the mapper reads it for every row, so a derived
 * {@code findByDestinationId} would issue one query for the areas and then one more per area — and
 * it would do so invisibly, because the code that triggers it lives in the mapper rather than in
 * the loop. Every list-returning method in this package fetches the source in the query for that
 * reason.
 */
public interface DestinationAreaJpaRepository extends JpaRepository<DestinationAreaEntity, UUID> {

    @Query("""
            select a from DestinationAreaEntity a
            join fetch a.source
            where a.destinationId = :destinationId
            order by a.slug asc
            """)
    List<DestinationAreaEntity> findByDestinationId(@Param("destinationId") UUID destinationId);
}
