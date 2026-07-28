package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * A normalised AI provider failure (task 14 — "provider error normalization").
 *
 * <p>Callers must never see an {@code AnthropicHttpException} or an OpenAI 429 body. If they did,
 * the retry decision, the user-facing message, and the metrics would all have to be written twice —
 * once per vendor — and the second one would be written slightly differently. Every adapter
 * translates into this type, and every consumer branches on {@link #retryable()}, not on a status
 * code.
 *
 * <p>One class with four factories rather than four subclasses: the variants differ only in code,
 * message, and retryability, and a hierarchy would tempt somebody to {@code catch} one of them
 * specifically — which is the coupling this type exists to prevent.
 *
 * <p>All four codes are registered in {@code api/openapi/errors.yaml}, because ADR 007 requires them
 * to be deliverable as an SSE {@code StreamError} frame carrying the §6.1 envelope: once a stream
 * has returned {@code 200}, an unregistered code renders to the user as a raw identifier.
 */
public class AiProviderException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String UNAVAILABLE = "ai_unavailable";
    public static final String TIMEOUT = "ai_timeout";
    public static final String RATE_LIMITED = "ai_rate_limited";
    public static final String RESPONSE_INVALID = "ai_response_invalid";

    private final transient boolean retryable;

    protected AiProviderException(String code, String message, boolean retryable) {
        super(code, message, Map.of());
        this.retryable = retryable;
    }

    protected AiProviderException(String code, String message, Map<String, Object> details) {
        super(code, message, details);
        this.retryable = false;
    }

    /**
     * Whether another attempt could plausibly succeed.
     *
     * <p>The retry policy reads this and nothing else. A misconfigured API key is not retryable and
     * retrying it three times only delays the error the operator needs to see.
     */
    public boolean retryable() {
        return retryable;
    }

    /** Provider unreachable, 5xx, or the circuit breaker is open. Retryable. */
    public static AiProviderException unavailable(String detail) {
        return new AiProviderException(UNAVAILABLE, "The AI provider is unavailable: " + detail, true);
    }

    /**
     * Not retryable, even though a timeout is transient by nature. The call already consumed its
     * full budget; retrying multiplies the wait a user is sitting through by the attempt count.
     * Bounded retry happens <em>inside</em> the timeout, not around it.
     */
    public static AiProviderException timeout(String detail) {
        return new AiProviderException(TIMEOUT, "The AI provider timed out: " + detail, false);
    }

    /** Provider quota exceeded. Retryable, with backoff. */
    public static AiProviderException rateLimited(String detail) {
        return new AiProviderException(RATE_LIMITED,
                "The AI provider rate-limited the request: " + detail, true);
    }

    /**
     * The model answered, but the answer failed schema validation and repair did not fix it.
     *
     * <p>Not retryable: a model that has already produced malformed output twice under the same
     * prompt is not going to produce valid output on the third identical attempt. PLAN §4.1 calls
     * for a typed "no confident result" instead of a hallucinated one — this is that signal.
     */
    public static AiProviderException responseInvalid(String detail) {
        return new AiProviderException(RESPONSE_INVALID,
                "The AI response did not match the required schema: " + detail, false);
    }
}
