package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.infrastructure.persistence.entity.UserIdentityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * {@link UserIdentityEntity} ↔ {@link UserIdentity}. Created by task 08 because registration writes
 * the {@code LOCAL} row and {@code GET /auth/me} reads every row for a user (UC-A11).
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface UserIdentityPersistenceMapper {

    UserIdentity toDomain(UserIdentityEntity entity);

    void applyToEntity(UserIdentity identity, @MappingTarget UserIdentityEntity entity);
}
