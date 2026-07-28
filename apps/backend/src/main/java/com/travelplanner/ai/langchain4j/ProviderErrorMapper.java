package com.travelplanner.ai.langchain4j;

import com.travelplanner.domain.exception.AiProviderException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;

/**
 * Vendor failure to {@link AiProviderException} (task 14 — "provider error normalization").
 *
 * <p>This is the only place in the system that knows what a provider failure looks like. Everything
 * upstream branches on {@link AiProviderException#retryable()}, which is what lets the retry policy,
 * the circuit breaker, and the SSE error frame be written once instead of once per vendor.
 *
 * <p>The mapping leans on LangChain4j's own {@code RetriableException}/{@code NonRetriableException}
 * split rather than re-deriving retryability from HTTP status codes. It already encodes the
 * per-provider knowledge — that an Anthropic {@code overloaded_error} is worth retrying and a
 * {@code 400 invalid_request} is not — and duplicating that here would mean maintaining it twice.
 *
 * <p><strong>Messages never carry the request.</strong> A provider's error body can quote the prompt
 * back, which would put a traveller's plans into logs and into an error response
 * (PLAN §9: no PII in logs). Only the exception's own class name and the cause chain's message are
 * used, and neither is echoed to the client — {@code GlobalExceptionHandler} sends the registered
 * code with a fixed message.
 */
final class ProviderErrorMapper {

    private ProviderErrorMapper() {
    }

    static AiProviderException map(String provider, Throwable failure) {
        if (failure instanceof AiProviderException already) {
            return already;
        }
        Throwable cause = unwrap(failure);

        if (cause instanceof TimeoutException || cause instanceof java.util.concurrent.TimeoutException) {
            return AiProviderException.timeout(provider);
        }
        if (cause instanceof RateLimitException) {
            return AiProviderException.rateLimited(provider);
        }
        if (cause instanceof AuthenticationException) {
            // Not "unavailable": the provider is fine and a retry cannot help. Surfacing this as a
            // transient fault would hide a bad key behind a retry loop and an intermittent-looking
            // error rate, which is how a rotated credential goes unnoticed for a day.
            return new ConfigurationFault(provider, "authentication rejected");
        }
        if (cause instanceof ModelNotFoundException) {
            return new ConfigurationFault(provider, "the configured model does not exist");
        }
        if (cause instanceof ContentFilteredException) {
            return AiProviderException.responseInvalid(provider + ": content filtered");
        }
        if (cause instanceof InvalidRequestException) {
            return AiProviderException.responseInvalid(provider + ": request rejected as invalid");
        }
        if (cause instanceof NonRetriableException) {
            return new ConfigurationFault(provider, cause.getClass().getSimpleName());
        }
        // Everything else — RetriableException, InternalServerException, transport faults — is a
        // provider that might work on the next attempt.
        return AiProviderException.unavailable(provider + ": " + cause.getClass().getSimpleName());
    }

    /**
     * Reactor and the streaming bridge wrap failures. The vendor exception is what carries the
     * classification, so the wrappers are peeled off before matching.
     */
    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && isWrapper(current)) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isWrapper(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.util.concurrent.ExecutionException
                || failure instanceof RuntimeException && failure.getClass() == RuntimeException.class;
    }

    /**
     * A permanent, operator-fixable fault reported as {@code ai_unavailable} but never retried.
     *
     * <p>It reuses the registered {@code ai_unavailable} code because the user-facing meaning is the
     * same — "the AI is not answering right now" — while {@code retryable()} stays {@code false} so
     * the platform does not burn three attempts on a request that cannot succeed.
     */
    private static final class ConfigurationFault extends AiProviderException {

        private static final long serialVersionUID = 1L;

        private ConfigurationFault(String provider, String detail) {
            super(UNAVAILABLE, "The AI provider is misconfigured (" + provider + "): " + detail,
                    false);
        }
    }
}
