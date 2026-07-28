package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.infrastructure.persistence.entity.RefreshTokenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * {@link RefreshTokenEntity} ↔ {@link RefreshToken}. Field names line up one-for-one, so the
 * mapping needs no configuration — but {@code unmappedTargetPolicy = ERROR} still makes a column
 * added on one side without the other a compile failure (PLAN §4.0.2-I).
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface RefreshTokenPersistenceMapper {

    RefreshToken toDomain(RefreshTokenEntity entity);

    void applyToEntity(RefreshToken token, @MappingTarget RefreshTokenEntity entity);
}
