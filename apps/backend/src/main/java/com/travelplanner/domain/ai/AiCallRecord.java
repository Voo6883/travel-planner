package com.travelplanner.domain.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code ai_call_log} (PLAN §5.3, §8; backlog S2-5).
 *
 * <h2>The prompt is not here, and cannot be added by accident</h2>
 *
 * <p>{@code AI-AGENT-WORKFLOW.md} A4 says "log tokens + latency — not full prompts in prod", and
 * PLAN §9 says no PII in logs. Those are usually implemented as a redaction step, which is a rule
 * somebody has to remember. Here it is enforced by the shape of the type: <strong>there is no field
 * capable of holding prompt text, completion text, user messages, or an email address.</strong> A
 * developer who wants to log a prompt has to change this record, the entity, the mapper, and a
 * migration — four reviewed edits — rather than pass one more argument.
 *
 * <p>What replaces the prompt is {@link #promptHash()}: a SHA-256 of the rendered prompt. It answers
 * the questions operations actually asks — "is this the same prompt that failed yesterday?", "did
 * the retry send something different?" — without being reversible into the traveller's plans.
 *
 * <p>{@code userId} is retained deliberately. It is an internal surrogate key, not personal data,
 * and without it per-user cost and abuse investigation are impossible. It is nullable because
 * background and system calls have no user.
 *
 * <h2>Field count</h2>
 *
 * <p>Fourteen components, against the ≤3-parameter rule (PLAN §4.0.4). That rule's own remedy is
 * "bundle into a {@code *Query}/{@code *Command}/{@code *Context}" — this record <em>is</em> the
 * bundle, and it is constructed in exactly one place ({@code ai/observability/AiCallRecorder}).
 *
 * @param requestId the {@code X-Request-Id} from the MDC (PLAN §4.0.2-J2), which is what ties an AI
 *     call to the HTTP request a user reported
 * @param promptHash SHA-256 hex of the rendered prompt; never the prompt
 * @param costAmount estimated spend, {@link BigDecimal} because it is money (PLAN §6, §13.1) — a
 *     {@code double} accumulating fractions of a cent across millions of calls drifts
 * @param errorCode the normalised {@code snake_case} code when {@code outcome == ERROR}, else
 *     {@code null}. Registered in {@code api/openapi/errors.yaml}
 */
public record AiCallRecord(UUID id, String requestId, UUID userId, String feature,
        AiOperation operation, String provider, String model, LlmEvent.Usage usage, long latencyMs,
        BigDecimal costAmount, Currency costCurrency, AiCallOutcome outcome, String errorCode,
        String promptHash, Instant createdAt) {

    public AiCallRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(createdAt, "createdAt");
        usage = usage == null ? LlmEvent.Usage.none() : usage;
        latencyMs = Math.max(0L, latencyMs);
        costAmount = costAmount == null ? BigDecimal.ZERO : costAmount;
        costCurrency = costCurrency == null ? Currency.getInstance("USD") : costCurrency;
        if (outcome != AiCallOutcome.ERROR && errorCode != null) {
            throw new IllegalArgumentException("errorCode is only meaningful for outcome=ERROR");
        }
    }
}
