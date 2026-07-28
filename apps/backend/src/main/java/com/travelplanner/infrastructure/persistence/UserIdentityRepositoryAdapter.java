package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.UserIdentity;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.UserIdentityEntity;
import com.travelplanner.infrastructure.persistence.mapper.UserIdentityPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.UserIdentityJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link UserIdentityRepositoryPort} over JPA. */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class UserIdentityRepositoryAdapter implements UserIdentityRepositoryPort {

    private final UserIdentityJpaRepository repository;
    private final UserIdentityPersistenceMapper mapper;

    public UserIdentityRepositoryAdapter(UserIdentityJpaRepository repository,
            UserIdentityPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public UserIdentity save(UserIdentity identity) {
        UserIdentityEntity entity = repository.findById(identity.id())
                .orElseGet(UserIdentityEntity::new);
        mapper.applyToEntity(identity, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public List<UserIdentity> findAllByUserId(UUID userId) {
        return repository.findAllByUserId(userId).stream().map(mapper::toDomain).toList();
    }
}
