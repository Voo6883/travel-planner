package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.infrastructure.persistence.entity.TravelAppEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link TravelAppEntity} → {@link TravelApp}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>The two store URLs are mapped independently and both may be null on their own. The record's
 * constructor is what enforces {@code ck_travel_app_has_a_store_link} in Java, so a row that somehow
 * lost both fails here rather than reaching a screen as an app nobody can install.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface TravelAppPersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    TravelApp toDomain(TravelAppEntity entity);
}
