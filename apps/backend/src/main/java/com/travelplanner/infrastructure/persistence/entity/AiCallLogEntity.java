package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code ai_call_log} (V11).
 *
 * <p>No MapStruct mapper, matching {@link MailRateLimitEntity} and {@link LoginAttemptEntity}: the
 * translation is a flat field copy in one direction only, and generating it would add a mapper
 * interface whose entire body is what the adapter already reads in ten lines.
 *
 * <p><strong>There is no prompt or completion field, and adding one requires changing this class, the
 * domain record, the adapter, and a migration.</strong> That is the enforcement mechanism for
 * AI-AGENT-WORKFLOW A4 — the rule is a shape, not a discipline.
 *
 * <p>No {@code @Version}. The table is append-only; nothing ever updates a row, so optimistic
 * locking would be a column that is always 0.
 */
@Entity
@Table(name = "ai_call_log")
public class AiCallLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "feature", nullable = false, length = 64)
    private String feature;

    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cached_tokens", nullable = false)
    private int cachedTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    /** {@code numeric(14,6)} — money is never a floating-point type (PLAN §13.1). */
    @Column(name = "cost_amount", nullable = false, precision = 14, scale = 6)
    private BigDecimal costAmount;

    /**
     * {@code char(3)}, not {@code varchar}. An ISO 4217 code is exactly three characters, and the
     * {@code @JdbcTypeCode} is what stops {@code ddl-auto: validate} rejecting the column as a
     * {@code varchar} mismatch — the same treatment {@link MailRateLimitEntity#getSubjectHash} needs.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cost_currency", nullable = false, length = 3)
    private String costCurrency;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** SHA-256 hex of the rendered prompt. Never the prompt. */
    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    public AiCallLogEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public int getCachedTokens() {
        return cachedTokens;
    }

    public void setCachedTokens(int cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public String getCostCurrency() {
        return costCurrency;
    }

    public void setCostCurrency(String costCurrency) {
        this.costCurrency = costCurrency;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public void setPromptHash(String promptHash) {
        this.promptHash = promptHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
