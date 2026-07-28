package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.UserEntity;
import com.travelplanner.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.UserJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        // Loaded first when the row already exists, so an update carries the persistence context's
        // identity instead of arriving as a detached instance that overwrites unread columns.
        UserEntity entity = repository.findById(user.id()).orElseGet(UserEntity::new);
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
    public Optional<User> findByUsernameIgnoreCase(String username) {
        return repository.findByUsernameIgnoreCase(username).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID userId) {
        return repository.existsById(userId);
    }

    /**
     * The admin list (UC-A15). {@code id} is a secondary sort key rather than decoration: without it
     * two accounts created in the same millisecond are free to swap places between two page
     * requests, so one appears on both pages and the other on neither.
     */
    @Override
    public List<User> findPage(int page, int pageSize, boolean newestFirst) {
        Sort order = Sort.by(newestFirst ? Sort.Direction.DESC : Sort.Direction.ASC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
        return repository.findAll(PageRequest.of(page, pageSize, order))
                .map(mapper::toDomain)
                .getContent();
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUsernameIgnoreCase(String username) {
        return repository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public int revokeSessions(UUID userId, Instant sessionsValidAfter) {
        return repository.revokeSessions(userId, sessionsValidAfter);
    }
}
