package com.travelplanner.ai.observability;

import com.travelplanner.domain.ai.AiCallOutcome;
import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.AiCallLogPort;
import java.time.Clock;
import java.util.Currency;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Builds and persists {@code ai_call_log} rows (PLAN §5.3; backlog S2-5 "token count persisted").
 *
 * <p>The <strong>only</strong> place {@link AiCallRecord} is constructed, which is what makes the
 * no-prompts guarantee reviewable: there is one file to read to confirm that no prompt text, no
 * completion text, and no personal data ever reaches the table. Everything the row carries is either
 * a count, a duration, an identifier, or a hash.
 *
 * <p>{@code X-Request-Id} is read from the MDC (PLAN §4.0.2-J2) rather than threaded through every
 * signature. It is already there for logging, and an AI call that cannot be tied back to the HTTP
 * request a user complained about is most of the way to useless.
 *
 * <p>Failures here are swallowed and logged. An observability write must never turn a working trip
 * plan into a 500 — the metrics row is the cheaper thing to lose.
 */
public final class AiCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiCallRecorder.class);

    private static final String REQUEST_ID_KEY = "requestId";
    private static final Currency USD = Currency.getInstance("USD");

    private final AiCallLogPort callLog;
    private final Clock clock;

    public AiCallRecorder(AiCallLogPort callLog, Clock clock) {
        this.callLog = callLog;
        this.clock = clock;
    }

    /** Records a successful call. */
    public void recordSuccess(AiCallContext context, LlmEvent.Usage usage, long latencyMs) {
        persist(context, new Result(usage, latencyMs, AiCallOutcome.OK, null));
    }

    /**
     * Records a failed call.
     *
     * @param errorCode the normalised {@code snake_case} code, never a vendor message. A provider's
     *     error text can quote the prompt back, which would defeat the whole point of storing a hash
     *     instead of the prompt; a registered code carries the same operational meaning and no
     *     traveller data. It also makes the column groupable — "how many {@code ai_timeout} this
     *     hour" is a query, where free text is not.
     */
    public void recordFailure(AiCallContext context, String errorCode, long latencyMs) {
        persist(context, new Result(LlmEvent.Usage.none(), latencyMs, AiCallOutcome.ERROR,
                errorCode == null ? AiProviderException.UNAVAILABLE : errorCode));
    }

    /**
     * Records a cancelled call. Separate from failure so a user closing a browser tab does not
     * inflate the error rate the on-call engineer is looking at.
     */
    public void recordCancelled(AiCallContext context, LlmEvent.Usage usage, long latencyMs) {
        persist(context, new Result(usage, latencyMs, AiCallOutcome.CANCELLED, null));
    }

    /** The four values that describe how a call ended — bundled to keep {@code persist} at 2 params. */
    private record Result(LlmEvent.Usage usage, long latencyMs, AiCallOutcome outcome,
            String errorCode) {
    }

    private void persist(AiCallContext context, Result result) {
        try {
            callLog.record(new AiCallRecord(
                    UUID.randomUUID(),
                    MDC.get(REQUEST_ID_KEY),
                    context.userId(),
                    context.feature(),
                    context.operation(),
                    context.provider(),
                    context.model(),
                    result.usage(),
                    result.latencyMs(),
                    AiCostEstimator.estimate(context.model(), result.usage()),
                    USD,
                    result.outcome(),
                    result.errorCode(),
                    context.promptHash(),
                    clock.instant()));
        } catch (RuntimeException failure) {
            log.warn("Could not persist ai_call_log row for feature={} provider={}",
                    context.feature(), context.provider(), failure);
        }
    }
}
