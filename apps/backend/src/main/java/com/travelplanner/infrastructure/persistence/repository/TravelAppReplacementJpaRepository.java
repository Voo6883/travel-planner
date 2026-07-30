package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.TravelAppReplacementEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code travel_app_replacement} (V21).
 *
 * <p>{@code join fetch r.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned.
 */
public interface TravelAppReplacementJpaRepository
        extends JpaRepository<TravelAppReplacementEntity, UUID> {

    /**
     * Every replacement declared by any app in one country — the query that builds a pack.
     *
     * <p>Joined through {@code travel_app} rather than filtered on a {@code country_code} column of
     * its own. V21 does not have one on purpose: {@code travel_app.country_code} already holds the
     * fact, and a copy could disagree with it — a curator fixing a mis-filed app's country would
     * leave the replacement applying in the old one.
     */
    @Query("""
            select r from TravelAppReplacementEntity r
            join fetch r.source
            join TravelAppEntity a on a.id = r.localAppId
            where a.countryCode = :countryCode
            order by r.replacedAppKey asc
            """)
    List<TravelAppReplacementEntity> findByCountryCode(@Param("countryCode") String countryCode);

    /** Every replacement a specific local app declares. Used by the seed loader's idempotence. */
    @Query("""
            select r from TravelAppReplacementEntity r
            join fetch r.source
            where r.localAppId = :localAppId
            order by r.replacedAppKey asc
            """)
    List<TravelAppReplacementEntity> findByLocalAppId(@Param("localAppId") UUID localAppId);
}
