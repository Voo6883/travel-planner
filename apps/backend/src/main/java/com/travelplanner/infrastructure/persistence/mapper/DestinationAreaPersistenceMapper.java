package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.infrastructure.persistence.entity.DestinationAreaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link DestinationAreaEntity} → {@link DestinationArea}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why, and for the
 * {@code numeric(9,6)} → {@link Double} narrowing the coordinates go through.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface DestinationAreaPersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    DestinationArea toDomain(DestinationAreaEntity entity);
}
