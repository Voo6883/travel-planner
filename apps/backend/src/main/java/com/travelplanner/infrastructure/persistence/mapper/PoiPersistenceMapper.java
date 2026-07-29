package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Poi;
import com.travelplanner.infrastructure.persistence.entity.PoiEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link PoiEntity} → {@link Poi}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>{@code tags} is copied rather than aliased: MapStruct builds a new list, and the record's
 * constructor then wraps it with {@code List.copyOf}. Both steps matter. The entity's list is
 * attached to a persistence context, and a caller mutating it through the domain record would be
 * editing a managed collection — silently scheduling an UPDATE and desynchronising the row from the
 * vector ADR 010 §5 built out of {@code name + description + tags}.
 *
 * <p>{@code openingHours} and {@code priceBand} carry the 90-day TTL of ADR 010 §6. They are mapped
 * as plain nullable fields on purpose: staleness is answered from {@code provenance}, computed
 * against the clock, never stored as a flag that would be wrong the moment after it was written.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface PoiPersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    Poi toDomain(PoiEntity entity);
}
