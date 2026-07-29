package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.infrastructure.persistence.entity.DestinationGuideEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link DestinationGuideEntity} → {@link DestinationGuide}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>{@code source = "entity"} hands the whole row to {@link KnowledgeProvenanceMapper} rather than
 * just its {@code source} association, because the provenance needs the row's {@code retrieved_at}
 * as well as the source's licence. Passing only the source would compile and would be wrong.
 *
 * <p>{@code food} and {@code practical} pass through as nullable strings. An absent section is
 * honest and an empty one is not, so the mapper must not helpfully default them.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface DestinationGuidePersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    DestinationGuide toDomain(DestinationGuideEntity entity);
}
