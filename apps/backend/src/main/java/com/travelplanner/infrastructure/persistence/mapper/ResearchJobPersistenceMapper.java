package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.infrastructure.persistence.entity.ResearchJobEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link ResearchJobEntity} ↔ {@link ResearchJob}.
 *
 * <p>{@code version} is copied both ways for the same reason as {@code TripPersistenceMapper}:
 * omitting it from the update mapping would leave the detached entity carrying its loaded version
 * and silently disable optimistic locking.
 *
 * <p>{@code markRunning} and {@code complete} are ignored for the same reason
 * {@code ConversationPersistenceMapper} ignores its transitions: MapStruct reads any single-argument
 * method returning the enclosing type as a fluent setter, so these two domain transitions look like
 * writable properties. The other transitions ({@code markProgress}, {@code fail}) take two arguments
 * and are invisible to that heuristic. Stated per property rather than by relaxing the
 * unmapped-target policy, so a genuinely forgotten column still fails the build.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface ResearchJobPersistenceMapper {

    @Mapping(target = "markRunning", ignore = true)
    @Mapping(target = "complete", ignore = true)
    ResearchJob toDomain(ResearchJobEntity entity);

    void applyToEntity(ResearchJob job, @MappingTarget ResearchJobEntity entity);
}
