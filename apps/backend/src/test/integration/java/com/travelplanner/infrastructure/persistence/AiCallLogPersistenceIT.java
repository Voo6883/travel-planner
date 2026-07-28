package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.ai.AiCallOutcome;
import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.ai.AiOperation;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.infrastructure.persistence.repository.AiCallLogJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ai_call_log} against a real Postgres (V11).
 *
 * <p>The CHECK constraints are the point. They are the last line of defence for a table whose rows
 * are written by a component that deliberately swallows its own failures — a bug in the recorder
 * would otherwise produce silently wrong cost and error-rate data rather than an exception.
 */
class AiCallLogPersistenceIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiCallLogRepositoryAdapter adapter;

    @Autowired
    private AiCallLogJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The adapter commits in its own transaction ({@code REQUIRES_NEW}), by design — a metrics row
     * must survive the rollback of the operation it documents. That also means the usual
     * test-transaction rollback cannot clean up after it, so the table is cleared explicitly.
     */
    @org.junit.jupiter.api.BeforeEach
    void clearTheLog() {
        jdbcTemplate.update("delete from ai_call_log");
    }

    @Test
    void persistsTokensLatencyCostAndOutcome() {
        adapter.record(record(AiCallOutcome.OK, null));

        assertThat(repository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getProvider()).isEqualTo("anthropic");
            assertThat(row.getInputTokens()).isEqualTo(120);
            assertThat(row.getCachedTokens()).isEqualTo(40);
            assertThat(row.getLatencyMs()).isEqualTo(1_250L);
            assertThat(row.getCostAmount()).isEqualByComparingTo("0.000640");
            assertThat(row.getOutcome()).isEqualTo("OK");
        });
    }

    /** The stored guarantee, checked against the database rather than against the Java type. */
    @Test
    void hasNoColumnThatCouldHoldPromptOrCompletionText() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'ai_call_log'",
                String.class);

        assertThat(columns).contains("prompt_hash");
        assertThat(columns).doesNotContain("prompt", "prompt_text", "completion", "response",
                "messages", "content", "email");
    }

    @Test
    void rejectsAnErrorRowWithNoCode() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into ai_call_log (id, feature, operation, provider, outcome)
                values (?, 'chat', 'COMPLETE', 'anthropic', 'ERROR')
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_ai_call_log_error_code");
    }

    @Test
    void rejectsASuccessRowThatCarriesAnErrorCode() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into ai_call_log (id, feature, operation, provider, outcome, error_code)
                values (?, 'chat', 'COMPLETE', 'anthropic', 'OK', 'ai_timeout')
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_ai_call_log_error_code");
    }

    @Test
    void rejectsAnUnknownOutcome() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into ai_call_log (id, feature, operation, provider, outcome)
                values (?, 'chat', 'COMPLETE', 'anthropic', 'MAYBE')
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_ai_call_log_outcome");
    }

    @Test
    void rejectsNegativeTokenCounts() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into ai_call_log (id, feature, operation, provider, outcome, input_tokens)
                values (?, 'chat', 'COMPLETE', 'anthropic', 'OK', -1)
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_ai_call_log_tokens");
    }

    /** Cost is {@code numeric}, so six decimal places survive the round trip intact. */
    @Test
    void storesCostWithoutFloatingPointDrift() {
        adapter.record(record(AiCallOutcome.OK, null));

        BigDecimal stored = jdbcTemplate.queryForObject(
                "select cost_amount from ai_call_log", BigDecimal.class);

        assertThat(stored).isEqualByComparingTo(new BigDecimal("0.000640"));
    }

    private static AiCallRecord record(AiCallOutcome outcome, String errorCode) {
        return new AiCallRecord(UUID.randomUUID(), "req-1", null, "chat", AiOperation.COMPLETE,
                "anthropic", "claude-sonnet-4-5", new LlmEvent.Usage(120, 30, 40), 1_250L,
                new BigDecimal("0.000640"), Currency.getInstance("USD"), outcome, errorCode,
                "a".repeat(64), Instant.parse("2026-07-28T10:00:00Z"));
    }
}
