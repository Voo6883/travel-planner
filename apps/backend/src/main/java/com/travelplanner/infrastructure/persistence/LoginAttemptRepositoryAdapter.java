package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.port.LoginAttemptPort;
import com.travelplanner.infrastructure.persistence.entity.LoginAttemptEntity;
import com.travelplanner.infrastructure.persistence.repository.LoginAttemptJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LoginAttemptPort} over JPA (ADR 009 §6).
 *
 * <p>{@link #recordFailure} runs in its own transaction. A failed sign-in ends in an exception, and
 * if the counter shared the caller's transaction it would be rolled back by the very failure it is
 * counting — producing a lockout that can never trigger. {@code REQUIRES_NEW} makes the record
 * survive independently of what the caller does next.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class LoginAttemptRepositoryAdapter implements LoginAttemptPort {

    private final LoginAttemptJpaRepository repository;

    public LoginAttemptRepositoryAdapter(LoginAttemptJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String identifier, String clientIp) {
        LoginAttemptEntity attempt = new LoginAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setLoginIdentifier(identifier);
        attempt.setClientIp(clientIp);
        attempt.setAttemptedAt(Instant.now());
        repository.saveAndFlush(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public int countFailuresSince(LoginAttemptKey key, Instant since) {
        long count = repository.countFailures(key.identifier(), key.clientIp(), since);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Override
    @Transactional
    public void clearFailures(String identifier, String clientIp) {
        repository.deleteForKey(identifier, clientIp);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
