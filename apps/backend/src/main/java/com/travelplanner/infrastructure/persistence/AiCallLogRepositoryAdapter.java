package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.port.AiCallLogPort;
import com.travelplanner.infrastructure.persistence.entity.AiCallLogEntity;
import com.travelplanner.infrastructure.persistence.repository.AiCallLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AiCallLogPort} over JPA (V11, PLAN §5.3).
 *
 * <p>{@code REQUIRES_NEW}, for the same reason {@link MailRateLimitRepositoryAdapter#record} uses it:
 * the operation being recorded may itself be failing, and a metrics row rolled back by the very
 * failure it documents is a log that is empty exactly when it is needed.
 *
 * <p>Failures are swallowed and logged. Observability must never be able to fail a user's request —
 * losing one row is strictly cheaper than turning a working trip plan into a 500.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class AiCallLogRepositoryAdapter implements AiCallLogPort {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogRepositoryAdapter.class);

    private final AiCallLogJpaRepository repository;

    public AiCallLogRepositoryAdapter(AiCallLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiCallRecord call) {
        try {
            repository.save(toEntity(call));
        } catch (RuntimeException failure) {
            log.warn("Could not persist ai_call_log row for feature={} provider={}", call.feature(),
                    call.provider(), failure);
        }
    }

    /**
     * Field-by-field, on purpose.
     *
     * <p>A reflective or generated mapper would happily copy a field somebody added later — including
     * one holding prompt text. Writing the copy out makes the table's contents a thing a reviewer
     * reads rather than infers.
     */
    private static AiCallLogEntity toEntity(AiCallRecord call) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setId(call.id());
        entity.setRequestId(call.requestId());
        entity.setUserId(call.userId());
        entity.setFeature(call.feature());
        entity.setOperation(call.operation().name());
        entity.setProvider(call.provider());
        entity.setModel(call.model());
        entity.setInputTokens(call.usage().inputTokens());
        entity.setOutputTokens(call.usage().outputTokens());
        entity.setCachedTokens(call.usage().cachedTokens());
        entity.setLatencyMs(call.latencyMs());
        entity.setCostAmount(call.costAmount());
        entity.setCostCurrency(call.costCurrency().getCurrencyCode());
        entity.setOutcome(call.outcome().name());
        entity.setErrorCode(call.errorCode());
        entity.setPromptHash(call.promptHash() == null ? "" : call.promptHash());
        entity.setCreatedAt(call.createdAt());
        return entity;
    }
}
