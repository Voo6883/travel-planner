package com.travelplanner.ai.resilience;

import com.travelplanner.config.AiProperties;
import com.travelplanner.domain.exception.AiProviderException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded retry with exponential backoff for AI calls (task 14 Scope; PLAN §9).
 *
 * <p>Hand-rolled rather than {@code @Retryable}, for two reasons that matter here. Spring Retry works
 * by AOP proxy, and the calls being retried happen inside adapters that are constructed directly by
 * {@code AiConfig} rather than proxied beans — the annotation would silently do nothing. And the
 * retry decision is not "which exception class" but {@link AiProviderException#retryable()}, a value
 * the mapper computed from vendor-specific knowledge; encoding it as a {@code retryFor} list would
 * duplicate that classification in an annotation nobody updates.
 *
 * <h2>What is not retried</h2>
 *
 * <ul>
 *   <li><strong>Timeouts.</strong> The call already spent its whole budget; a second attempt makes a
 *       user wait twice as long for the same failure.</li>
 *   <li><strong>Anything non-retryable</strong> — bad key, unknown model, invalid request. Retrying
 *       these only hides an operator-fixable fault behind an intermittent-looking error rate.</li>
 *   <li><strong>Streams.</strong> A stream that failed after emitting text cannot be retried: the
 *       consumer has already rendered the first attempt's tokens, and a second attempt would append
 *       a different answer to them. ADR 007 makes the failure explicit instead, as a
 *       {@code StreamError} frame.</li>
 * </ul>
 */
public final class AiRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(AiRetryPolicy.class);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Sleeper sleeper;

    /** Indirection so a unit test can assert on backoff without actually sleeping for it. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public AiRetryPolicy(AiProperties.Resilience settings, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, settings.getMaxAttempts());
        this.initialBackoff = settings.getInitialBackoff();
        this.multiplier = Math.max(1.0, settings.getBackoffMultiplier());
        this.sleeper = sleeper;
    }

    public static AiRetryPolicy of(AiProperties.Resilience settings) {
        return new AiRetryPolicy(settings, duration -> Thread.sleep(duration.toMillis()));
    }

    /** @return the call's result, or rethrows the last failure once attempts are exhausted */
    public <T> T execute(Supplier<T> call) {
        AiProviderException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (AiProviderException failure) {
                last = failure;
                if (!failure.retryable() || attempt == maxAttempts) {
                    throw failure;
                }
                log.warn("AI call failed ({}), attempt {}/{}", failure.code(), attempt, maxAttempts);
                backOff(attempt);
            }
        }
        throw last == null ? AiProviderException.unavailable("no attempt was made") : last;
    }

    private void backOff(int attempt) {
        long millis = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1.0));
        try {
            sleeper.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException interrupted) {
            // Restore the flag and give up: the thread is being shut down, and swallowing this would
            // make the pool un-stoppable while it finishes a retry nobody is waiting for.
            Thread.currentThread().interrupt();
            throw AiProviderException.unavailable("retry interrupted");
        }
    }
}
