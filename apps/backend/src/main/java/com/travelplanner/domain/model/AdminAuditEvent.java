package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.AdminAction;
import com.travelplanner.domain.enums.AdminActionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One administrative mutation, as it will be read months later by somebody asking "who did this?"
 * (PLAN §4.0.6 "every admin action writes to {@code audit_event} — who, what, target user, when").
 *
 * <h2>What it deliberately does not contain</h2>
 *
 * <p>No password, no password hash, no email address, and no free-text note. The reset action's
 * whole purpose is a new password, and a field able to hold one would eventually hold one — so the
 * type has none, and putting it there would require changing this record, the entity, the adapter,
 * and a migration. The same structural argument {@code AiCallRecord} makes about prompts.
 *
 * <p>Ids are kept, and are not personal data: they are internal surrogate keys, and without them
 * the trail cannot answer either half of "who did this to whom".
 *
 * @param requestId the {@code X-Request-Id} the action arrived on (PLAN §4.0.2-J2), or {@code null}
 *        for an action with no HTTP request behind it. It is what joins an audit row to the log
 *        lines the same request produced
 */
public record AdminAuditEvent(
        UUID id,
        UUID actorUserId,
        UUID targetUserId,
        AdminAction action,
        AdminActionResult result,
        String requestId,
        Instant occurredAt) {

    public AdminAuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(targetUserId, "targetUserId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requestId = blankToNull(requestId);
    }

    /**
     * The ordinary construction path. The id and the timestamp are generated here rather than taken
     * from the caller, so no call site can supply an id that collides or a time that flatters.
     *
     * <p>The result starts {@link AdminActionResult#SUCCESS} and {@link #withResult} is what
     * downgrades it. That direction is deliberate: the row is only ever written on the committed
     * path, so success is the true default, and a caller that has detected a degraded outcome has
     * to say so explicitly rather than a caller that has not having to remember to say nothing.
     */
    public static AdminAuditEvent of(UUID actorUserId, UUID targetUserId, AdminAction action) {
        return new AdminAuditEvent(UUID.randomUUID(), actorUserId, targetUserId, action,
                AdminActionResult.SUCCESS, null, Instant.now());
    }

    public AdminAuditEvent withResult(AdminActionResult newResult) {
        return new AdminAuditEvent(id, actorUserId, targetUserId, action, newResult, requestId,
                occurredAt);
    }

    /** The same event, correlated to the request that caused it. */
    public AdminAuditEvent onRequest(String newRequestId) {
        return new AdminAuditEvent(id, actorUserId, targetUserId, action, result, newRequestId,
                occurredAt);
    }

    public Optional<String> requestIdIfPresent() {
        return Optional.ofNullable(requestId);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
