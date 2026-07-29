package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.SeasonalityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code seasonality} (V17).
 *
 * <p>{@code join fetch s.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned, and
 * a FULL destination always has twelve of them.
 *
 * <p>{@code order by s.month} is part of the contract rather than a convenience. ADR 010 §1 requires
 * all twelve months before a destination counts as FULL, and a caller checking "do I have a year"
 * against an unordered list is one step from checking it against a list with June twice.
 */
public interface SeasonalityJpaRepository extends JpaRepository<SeasonalityEntity, UUID> {

    @Query("""
            select s from SeasonalityEntity s
            join fetch s.source
            where s.destinationId = :destinationId
            order by s.month asc
            """)
    List<SeasonalityEntity> findByDestinationId(@Param("destinationId") UUID destinationId);
}
