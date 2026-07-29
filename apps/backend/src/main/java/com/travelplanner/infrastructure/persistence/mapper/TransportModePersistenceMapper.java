package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.infrastructure.persistence.entity.TransportModeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link TransportModeEntity} → {@link TransportMode}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>{@code touristFriendly} is primitive on both sides, so there is no null to lose in translation.
 * That is the point: the field is a curated judgement, and a {@code Boolean} arriving as null would
 * be indistinguishable from "no, and we checked".
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface TransportModePersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    TransportMode toDomain(TransportModeEntity entity);
}
