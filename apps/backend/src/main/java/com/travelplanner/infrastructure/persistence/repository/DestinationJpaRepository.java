package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.infrastructure.persistence.entity.DestinationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code destination} (V14).
 *
 * <p>The one knowledge repository with <strong>no {@code join fetch}</strong>, and deliberately so:
 * {@code destination} carries no {@code source_id}, because a destination is an identifier for a
 * place rather than a claim about it. Every other repository in this package fetches its source
 * eagerly; this one has nothing to fetch, which is the difference worth noticing rather than a
 * missing clause.
 */
public interface DestinationJpaRepository extends JpaRepository<DestinationEntity, UUID> {

    Optional<DestinationEntity> findBySlug(String slug);

    /**
     * Destinations at one coverage level, in slug order.
     *
     * <p>Written as an explicit query rather than a derived {@code ...OrderBySlugAsc} so the method
     * name stays the one the port calls. Ordering is not cosmetic: ADR 010 §4 has the refusal path
     * quote the supported list back to the user, and a list whose order changes between calls looks
     * like the catalogue changed.
     */
    @Query("select d from DestinationEntity d where d.coverageLevel = :coverageLevel order by d.slug asc")
    List<DestinationEntity> findByCoverageLevel(@Param("coverageLevel") CoverageLevel coverageLevel);
}
