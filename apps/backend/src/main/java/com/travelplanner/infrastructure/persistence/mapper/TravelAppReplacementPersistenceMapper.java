package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.infrastructure.persistence.entity.TravelAppReplacementEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link TravelAppReplacementEntity} → {@link TravelAppReplacement}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>The record's constructor re-validates the slug shape that
 * {@code ck_travel_app_replacement_key_is_slug} enforces in SQL. That is not redundant: a row written
 * before the constraint existed, or through a path that bypassed it, would otherwise reach a screen
 * as a suppression that silently matches nothing.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface TravelAppReplacementPersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    TravelAppReplacement toDomain(TravelAppReplacementEntity entity);
}
