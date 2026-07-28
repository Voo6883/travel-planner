package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.model.AccountToken;
import com.travelplanner.domain.port.AccountTokenPort;
import com.travelplanner.infrastructure.persistence.entity.AccountTokenEntity;
import com.travelplanner.infrastructure.persistence.mapper.AccountTokenPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.AccountTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link AccountTokenPort} over JPA (V8, §4.0.10). */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class AccountTokenRepositoryAdapter implements AccountTokenPort {

    private final AccountTokenJpaRepository repository;
    private final AccountTokenPersistenceMapper mapper;

    public AccountTokenRepositoryAdapter(AccountTokenJpaRepository repository,
            AccountTokenPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AccountToken save(AccountToken token) {
        // Reuses the row when the id is already known, so consuming a token is an UPDATE rather
        // than a second row competing for the same unique digest.
        AccountTokenEntity entity = repository.findById(token.id()).orElseGet(AccountTokenEntity::new);
        mapper.applyToEntity(token, entity);
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public int consume(UUID tokenId, Instant consumedAt) {
        return repository.consume(tokenId, consumedAt);
    }

    @Override
    @Transactional
    public int consumeAllForUser(UUID userId, AccountTokenPurpose purpose, Instant consumedAt) {
        return repository.consumeAllForUser(userId, purpose, consumedAt);
    }

    @Override
    @Transactional
    public int purgeExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }
}
