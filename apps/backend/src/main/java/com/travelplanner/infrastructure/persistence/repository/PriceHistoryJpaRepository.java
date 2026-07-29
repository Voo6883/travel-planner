package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.PriceHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code price_history} (V17).
 *
 * <p>{@code join fetch h.source} for the N+1 reason spelled out in
 * {@link DestinationAreaJpaRepository}: the mapper reads the lazy source on every row returned.
 *
 * <p>Newest first, matching {@code ix_price_history_destination_category}, which is declared
 * {@code (destination_id, category, observed_on DESC)} for exactly this query. Callers want the
 * latest observation and the trend behind it; ascending order would make "most recent" the last
 * element of a list somebody has to remember to read backwards.
 */
public interface PriceHistoryJpaRepository extends JpaRepository<PriceHistoryEntity, UUID> {

    @Query("""
            select h from PriceHistoryEntity h
            join fetch h.source
            where h.destinationId = :destinationId and h.category = :category
            order by h.observedOn desc
            """)
    List<PriceHistoryEntity> findByDestinationIdAndCategoryOrderByObservedOnDesc(
            @Param("destinationId") UUID destinationId, @Param("category") String category);
}
