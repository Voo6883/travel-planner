package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.infrastructure.persistence.entity.RouteSegmentEntity;
import java.time.Duration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link RouteSegmentEntity} → {@link RouteSegment}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>The one hand-written conversion in this mapper is {@code duration_minutes integer} →
 * {@link Duration}. MapStruct has no built-in for it, which is fortunate: an implicit
 * {@code int} → {@code Duration} rule would have to pick a unit, and picking the wrong one is a
 * sixty-fold error in an itinerary rather than a compile failure. Naming the unit here makes the
 * choice reviewable.
 *
 * <p>The narrowing only loses precision in the other direction, and the record's constructor
 * rejects that case ("duration must be a whole number of minutes") rather than truncating — so a
 * value written by any future write path cannot come back different from what was stored.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface RouteSegmentPersistenceMapper {

    @Mapping(target = "duration", source = "durationMinutes")
    @Mapping(target = "provenance", source = "entity")
    RouteSegment toDomain(RouteSegmentEntity entity);

    /** {@code duration_minutes} is minutes, as the column name says. */
    default Duration toDuration(int durationMinutes) {
        return Duration.ofMinutes(durationMinutes);
    }
}
