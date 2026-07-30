package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.domain.port.RefreshTokenPort;
import com.travelplanner.infrastructure.persistence.entity.RefreshTokenEntity;
import com.travelplanner.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link RefreshTokenPort} over JPA (ADR 009 §3). */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class RefreshTokenRepositoryAdapter implements RefreshTokenPort {

    private final RefreshTokenJpaRepository repository;
    private final RefreshTokenPersistenceMapper mapper;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository repository,
            RefreshTokenPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        // Reuses the existing row when the id is already known, which is what makes rotation an
        // UPDATE of rotated_at rather than a second row competing for the same unique hash.
        RefreshTokenEntity entity = repository.findById(token.id()).orElseGet(RefreshTokenEntity::new);
        mapper.applyToEntity(token, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public boolean markRotated(String tokenHash, Instant rotatedAt) {
        // One statement, one row at most — `ux_refresh_token_hash` makes the predicate a unique
        // index lookup, so "did I win the race" costs the same as the read it replaces.
        return repository.markRotated(tokenHash, rotatedAt) == 1;
    }

    @Override
    public int revokeAllForUser(UUID userId, Instant revokedAt) {
        return repository.revokeAllForUser(userId, revokedAt);
    }
}
