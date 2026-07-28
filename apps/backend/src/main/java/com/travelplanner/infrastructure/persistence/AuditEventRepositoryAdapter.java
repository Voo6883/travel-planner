package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.AdminAuditEvent;
import com.travelplanner.domain.port.AdminAuditPort;
import com.travelplanner.infrastructure.persistence.entity.AuditEventEntity;
import com.travelplanner.infrastructure.persistence.repository.AuditEventJpaRepository;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link AdminAuditPort} over JPA (V12, PLAN §4.0.6).
 *
 * <h2>Two deliberate differences from {@link AiCallLogRepositoryAdapter}</h2>
 *
 * <p><b>No {@code REQUIRES_NEW}.</b> This write joins whatever transaction the caller is in, so the
 * audit row commits with the change it describes and rolls back with it. The AI log wants the
 * opposite — a metrics row that survives the failure it documents — but a surviving audit row for a
 * rolled-back mutation would assert that an administrator reset somebody's password when they did
 * not, and an audit trail that can lie is worse than none.
 *
 * <p><b>No {@code catch}.</b> The AI adapter swallows its failures because losing one metrics row is
 * cheaper than turning a working trip plan into a 500. Here the trade runs the other way: PLAN
 * §4.0.6 requires <em>every</em> admin mutation to be audited, and an unrecorded mutation must fail
 * rather than quietly succeed.
 *
 * <p>{@code saveAndFlush} rather than {@code save}, so a constraint violation — an {@code action}
 * outside the CHECK list, say — surfaces here rather than at commit, where it would already be past
 * the point the caller could report it accurately.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class AuditEventRepositoryAdapter implements AdminAuditPort {

    /**
     * The key {@code RequestIdFilter} publishes. Duplicated as a literal rather than imported, for
     * the reason {@code AiCallRecorder} duplicates it too: {@code infrastructure} must not depend on
     * {@code api}, and one string is a cheaper coupling than that direction of import.
     */
    private static final String REQUEST_ID_KEY = "requestId";

    private final AuditEventJpaRepository repository;

    public AuditEventRepositoryAdapter(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(AdminAuditEvent event) {
        repository.saveAndFlush(toEntity(event.onRequest(currentRequestId())));
    }

    /**
     * Field by field, on purpose — the same argument {@link AiCallLogRepositoryAdapter} makes. A
     * reflective or generated mapper would happily copy a field somebody adds later, and the field
     * most likely to be added to an admin action is the password it set.
     */
    private static AuditEventEntity toEntity(AdminAuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(event.id());
        entity.setActorUserId(event.actorUserId());
        entity.setTargetUserId(event.targetUserId());
        entity.setAction(event.action().name());
        entity.setResult(event.result().name());
        entity.setRequestId(event.requestId());
        entity.setCreatedAt(event.occurredAt());
        return entity;
    }

    /**
     * Read here rather than passed down from the controller. The correlation id is a property of the
     * request rather than of the action, and threading it through two service signatures would spend
     * two of the three parameters {@code AGENTS.md} allows on plumbing.
     *
     * @return the id {@code RequestIdFilter} put in the MDC, or {@code null} for an action with no
     *     HTTP request behind it
     */
    private static String currentRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }
}
