package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.infrastructure.persistence.entity.PlannerSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link PlannerSessionEntity} ↔ {@link PlannerSession}.
 *
 * <p>Both directions, because the session is written as well as read: {@code ended_at} is stamped
 * at the {@code create_trip} handoff.
 *
 * <p><strong>Why {@code end} is ignored.</strong> MapStruct treats any single-argument method that
 * returns the enclosing type as a fluent setter, so {@link PlannerSession#end(java.time.Instant)} —
 * a domain transition — looks to it like a writable property. It is not one, and the ignore says so
 * explicitly rather than reaching for a method-level {@code unmappedTargetPolicy}, which would also
 * hide a genuinely forgotten column. {@code PersistenceMapperConfig}'s ERROR policy is worth
 * keeping intact.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface PlannerSessionPersistenceMapper {

    @Mapping(target = "end", ignore = true)
    PlannerSession toDomain(PlannerSessionEntity entity);

    void applyToEntity(PlannerSession session, @MappingTarget PlannerSessionEntity entity);
}
