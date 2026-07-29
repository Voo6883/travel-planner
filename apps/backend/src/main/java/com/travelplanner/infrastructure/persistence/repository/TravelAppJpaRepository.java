package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.TravelAppEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code travel_app} (V16).
 *
 * <p>Keyed by country, not by destination — Grab is useful across Thailand, not only in Bangkok.
 * The caller reads {@code destination.countryCode} and asks here.
 *
 * <p>{@code join fetch t.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned.
 */
public interface TravelAppJpaRepository extends JpaRepository<TravelAppEntity, UUID> {

    @Query("""
            select t from TravelAppEntity t
            join fetch t.source
            where t.countryCode = :countryCode
            order by t.category asc, t.slug asc
            """)
    List<TravelAppEntity> findByCountryCode(@Param("countryCode") String countryCode);
}
