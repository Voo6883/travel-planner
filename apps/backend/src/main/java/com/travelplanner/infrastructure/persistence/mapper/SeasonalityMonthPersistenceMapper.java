package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.infrastructure.persistence.entity.SeasonalityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link SeasonalityEntity} → {@link SeasonalityMonth}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>{@code month} widens from the entity's {@code short} (the column is {@code smallint}, and
 * {@code ddl-auto: validate} compares the two) to the record's {@code int}. Widening is lossless in
 * this direction, so MapStruct's built-in assignment is the whole conversion; the range check that
 * matters — 1..12, mirroring {@code ck_seasonality_month_range} — belongs to the record and runs on
 * every row read.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface SeasonalityMonthPersistenceMapper {

    @Mapping(target = "provenance", source = "entity")
    SeasonalityMonth toDomain(SeasonalityEntity entity);
}
