package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.TransportModeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code transport_mode} (V16).
 *
 * <p>{@code join fetch m.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned.
 */
public interface TransportModeJpaRepository extends JpaRepository<TransportModeEntity, UUID> {

    @Query("""
            select m from TransportModeEntity m
            join fetch m.source
            where m.destinationId = :destinationId
            order by m.slug asc
            """)
    List<TransportModeEntity> findByDestinationId(@Param("destinationId") UUID destinationId);
}
