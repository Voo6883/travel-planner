package com.travelplanner.infrastructure.persistence.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct settings for every JPA entity ↔ domain mapper (PLAN §4.0.2-I).
 *
 * <p>{@code unmappedTargetPolicy = ERROR} is the reason this config exists. A field added to an
 * entity or to a domain record without a corresponding mapping is a compile failure, not a column
 * that silently reads back as {@code null} three tasks later. MapStruct's default is a warning,
 * which nobody reads in a Gradle log.
 *
 * <p>{@code componentModel = "spring"} makes each generated mapper an injectable bean, so adapters
 * receive it by constructor injection instead of holding a static {@code INSTANCE}.
 */
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PersistenceMapperConfig {
}
