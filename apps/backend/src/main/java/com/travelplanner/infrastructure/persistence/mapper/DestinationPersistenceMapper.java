package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Destination;
import com.travelplanner.infrastructure.persistence.entity.DestinationEntity;
import org.mapstruct.Mapper;

/**
 * {@link DestinationEntity} → {@link Destination}.
 *
 * <p>One direction only. Task 16's {@code KnowledgePort} is read-only: the catalogue is written by
 * the seeder and by task 41's curation UI, so an {@code applyToEntity} here would be an unused
 * write path that the next reader would assume is supported.
 *
 * <p>The coordinates cross a type boundary. The columns are {@code numeric(9,6)} and the entity
 * holds {@link java.math.BigDecimal}; the record holds {@link Double} because a coordinate is used
 * for distance arithmetic. MapStruct's built-in conversion narrows via {@code doubleValue()} and
 * keeps the null when the pair was never curated — which matters, because
 * {@code ck_destination_coordinates_paired} and the record's own constructor both treat a half-set
 * coordinate as an error rather than as a point on the equator.
 *
 * <p>No provenance: {@code destination} carries no {@code source_id}. See {@link DestinationEntity}.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface DestinationPersistenceMapper {

    Destination toDomain(DestinationEntity entity);
}
