package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.UserEntity;
import com.travelplanner.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.UserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link UserRepositoryPort} over JPA.
 *
 * <p>No optimistic-lock translation: {@code user} carries no {@code @Version} (ADR 008 §1). Tasks
 * 08 and 09 extend this adapter with registration, credential, and revocation operations.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository repository, UserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = new UserEntity();
        mapper.applyToEntity(user, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return repository.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        return repository.findByEmailIgnoreCase(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID userId) {
        return repository.existsById(userId);
    }
}
