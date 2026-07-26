package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Trip;
import com.travelplanner.infrastructure.persistence.entity.TripEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * {@link TripEntity} ↔ {@link Trip}.
 *
 * <p>{@code version} is copied in both directions on purpose. On the way out it is what the client
 * echoes back as {@code expected_version} (ADR 008 §2); on the way in it is what Hibernate compares
 * against the stored row. Omitting it from the update mapping would leave the detached entity
 * carrying whatever version it was loaded with and quietly disable optimistic locking.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface TripPersistenceMapper {

    Trip toDomain(TripEntity entity);

    void applyToEntity(Trip trip, @MappingTarget TripEntity entity);
}
