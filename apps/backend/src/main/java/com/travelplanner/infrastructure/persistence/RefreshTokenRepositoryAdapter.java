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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public RefreshToken save(RefreshToken token) {
        // Reuses the existing row when the id is already known, which is what makes rotation an
        // UPDATE of rotated_at rather than a second row competing for the same unique hash.
        RefreshTokenEntity entity = repository.findById(token.id()).orElseGet(RefreshTokenEntity::new);
        mapper.applyToEntity(token, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    /**
     * The claim, in a transaction of its own.
     *
     * <p><strong>Its own, and deliberately not the caller's.</strong> {@code RefreshTokenService.rotate}
     * is not transactional, so this is the only boundary the statement gets — which is all it needs,
     * because the atomicity lives in the {@code WHERE} clause rather than in the transaction. The
     * annotation is here because Spring Data requires one for a {@code @Modifying} query, and the same
     * reason {@link AccountTokenRepositoryAdapter} annotates its {@code consume} methods: a
     * single-statement state transition owns its own boundary.
     *
     * <p>Holding a caller's transaction open across this call is what caused a pool-starvation
     * deadlock — see {@code RefreshTokenService.rotate} for the full account.
     */
    @Override
    @Transactional
    public boolean markRotated(String tokenHash, Instant rotatedAt) {
        // One statement, one row at most — `ux_refresh_token_hash` makes the predicate a unique
        // index lookup, so "did I win the race" costs the same as the read it replaces.
        return repository.markRotated(tokenHash, rotatedAt) == 1;
    }

    @Override
    @Transactional
    public int revokeAllForUser(UUID userId, Instant revokedAt) {
        return repository.revokeAllForUser(userId, revokedAt);
    }
}
