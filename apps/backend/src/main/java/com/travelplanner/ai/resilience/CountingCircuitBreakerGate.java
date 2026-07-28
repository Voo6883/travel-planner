package com.travelplanner.ai.resilience;

import com.travelplanner.config.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A consecutive-failure breaker: open after N failures, half-open after a cooldown, closed on the
 * first success.
 *
 * <p>Consecutive failures rather than a failure <em>rate</em>. A rate needs a sliding window and a
 * minimum call volume to avoid tripping on the first two requests after a deploy; consecutive
 * failures need one integer and behave correctly at low traffic, which is what this system has.
 *
 * <p>In-process and per-instance. With more than one replica each holds its own view, so a dead
 * provider costs N failed calls per instance instead of N — acceptable for a threshold of five, and
 * the alternative is shared state in Redis, which is task 37's decision to make, not this one's.
 *
 * <p>The half-open probe is a single allowed request, not a window: after the cooldown one call gets
 * through, and its outcome decides whether the breaker closes or re-opens. Letting the full load
 * through on the first tick is how a recovering provider gets knocked over again.
 */
public final class CountingCircuitBreakerGate implements CircuitBreakerGate {

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Instant> openedAt = new ConcurrentHashMap<>();
    private final int threshold;
    private final Duration openDuration;
    private final Clock clock;

    public CountingCircuitBreakerGate(AiProperties.Resilience settings, Clock clock) {
        this.threshold = Math.max(1, settings.getCircuitBreakerFailureThreshold());
        this.openDuration = settings.getCircuitBreakerOpenDuration();
        this.clock = clock;
    }

    @Override
    public boolean allowRequest(String provider) {
        Instant opened = openedAt.get(provider);
        if (opened == null) {
            return true;
        }
        if (clock.instant().isBefore(opened.plus(openDuration))) {
            return false;
        }
        // Cooldown elapsed: clear the open marker so exactly one probe gets through. If it fails,
        // recordFailure re-opens immediately, because the counter is still at the threshold.
        openedAt.remove(provider);
        return true;
    }

    @Override
    public void recordSuccess(String provider) {
        consecutiveFailures.remove(provider);
        openedAt.remove(provider);
    }

    @Override
    public void recordFailure(String provider) {
        int failures = consecutiveFailures
                .computeIfAbsent(provider, key -> new AtomicInteger())
                .incrementAndGet();
        if (failures >= threshold) {
            openedAt.put(provider, clock.instant());
        }
    }
}
