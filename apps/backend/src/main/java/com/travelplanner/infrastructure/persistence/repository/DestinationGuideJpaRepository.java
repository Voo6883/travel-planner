package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.DestinationGuideEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code destination_guide} (V14).
 *
 * <p>{@code join fetch g.source} is not an N+1 fix here — one row cannot have an N+1 — but it is
 * still required. {@code source} is {@code LAZY}, the mapper dereferences it to build
 * {@code KnowledgeProvenance}, and a caller that maps after the transaction closed would get a
 * {@code LazyInitializationException} instead of a guide. Fetching in the query makes the mapping
 * work regardless of where the boundary happens to be.
 */
public interface DestinationGuideJpaRepository extends JpaRepository<DestinationGuideEntity, UUID> {

    @Query("""
            select g from DestinationGuideEntity g
            join fetch g.source
            where g.destinationId = :destinationId and g.locale = :locale
            """)
    Optional<DestinationGuideEntity> findByDestinationIdAndLocale(
            @Param("destinationId") UUID destinationId, @Param("locale") String locale);
}
