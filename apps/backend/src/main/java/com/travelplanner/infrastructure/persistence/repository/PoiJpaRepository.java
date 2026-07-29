package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.infrastructure.persistence.entity.PoiEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code poi} (V15).
 *
 * <p>The repository where N+1 hurts most: a full destination carries dozens of POIs, and the mapper
 * touches {@code source} on each one to build its provenance. Both finders therefore
 * <strong>{@code join fetch p.source}</strong> — without it a single "where should I eat in Tokyo"
 * turns one query into fifty, and the extra queries would be issued by the mapper rather than by
 * anything visible at the call site.
 *
 * <p>Neither method fetches {@code destination_area}: the entity holds {@code areaId} as a raw uuid
 * precisely so reading a POI never drags a neighbourhood along with it.
 */
public interface PoiJpaRepository extends JpaRepository<PoiEntity, UUID> {

    @Query("""
            select p from PoiEntity p
            join fetch p.source
            where p.destinationId = :destinationId
            order by p.slug asc
            """)
    List<PoiEntity> findByDestinationId(@Param("destinationId") UUID destinationId);

    /** Backed by {@code ix_poi_destination_category} — the index V15 exists to serve. */
    @Query("""
            select p from PoiEntity p
            join fetch p.source
            where p.destinationId = :destinationId and p.category = :category
            order by p.slug asc
            """)
    List<PoiEntity> findByDestinationIdAndCategory(
            @Param("destinationId") UUID destinationId, @Param("category") PoiCategory category);
}
