package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.AccountToken;
import com.travelplanner.infrastructure.persistence.entity.AccountTokenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * {@link AccountTokenEntity} ↔ {@link AccountToken}. Field names line up one-for-one, so the mapping
 * needs no configuration — but {@code unmappedTargetPolicy = ERROR} still makes a column added on
 * one side without the other a compile failure (PLAN §4.0.2-I).
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface AccountTokenPersistenceMapper {

    AccountToken toDomain(AccountTokenEntity entity);

    void applyToEntity(AccountToken token, @MappingTarget AccountTokenEntity entity);
}
