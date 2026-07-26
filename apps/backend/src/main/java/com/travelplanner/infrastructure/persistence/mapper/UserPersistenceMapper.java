package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.User;
import com.travelplanner.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * {@link UserEntity} ↔ {@link User}. Field names line up one-for-one, so MapStruct needs no
 * per-field configuration — but the mapping is still generated and still compile-checked, which is
 * the point: adding a column to the entity without adding it to the domain record breaks the build.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface UserPersistenceMapper {

    User toDomain(UserEntity entity);

    /**
     * Copies onto an existing managed or detached instance rather than returning a new one. A fresh
     * entity would drop the persistence context's identity and turn every update into a
     * detached-instance merge with its own version semantics.
     */
    void applyToEntity(User user, @MappingTarget UserEntity entity);
}
