package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.port.MailRateLimitPort;
import com.travelplanner.infrastructure.persistence.entity.MailRateLimitEntity;
import com.travelplanner.infrastructure.persistence.repository.MailRateLimitJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MailRateLimitPort} over JPA (V9, ADR 009 §6).
 *
 * <p>{@link #record} runs in its own transaction, for the same reason
 * {@link LoginAttemptRepositoryAdapter#recordFailure} does: the request being counted may end in an
 * exception — a rate-limited caller, a mail that fails to render — and a counter rolled back by the
 * very event it is counting is a limit that never limits.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class MailRateLimitRepositoryAdapter implements MailRateLimitPort {

    private final MailRateLimitJpaRepository repository;

    public MailRateLimitRepositoryAdapter(MailRateLimitJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public int countSince(String scope, String subjectHash, Instant since) {
        long count = repository.countSince(scope, subjectHash, since);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String scope, String subjectHash) {
        MailRateLimitEntity hit = new MailRateLimitEntity();
        hit.setId(UUID.randomUUID());
        hit.setScope(scope);
        hit.setSubjectHash(subjectHash);
        hit.setRequestedAt(Instant.now());
        repository.saveAndFlush(hit);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}
