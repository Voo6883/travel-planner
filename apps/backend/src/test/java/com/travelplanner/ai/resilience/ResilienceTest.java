package com.travelplanner.ai.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.AiProperties;
import com.travelplanner.domain.exception.AiProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Bounded retry and the circuit-breaker seam (task 14 Scope; PLAN §9). */
class ResilienceTest {

    private final AiProperties.Resilience settings = new AiProperties.Resilience();
    private final List<Duration> slept = new ArrayList<>();

    // ---------------------------------------------------------------------------------------
    // Retry
    // ---------------------------------------------------------------------------------------

    @Test
    void returnsTheFirstSuccessWithoutSleeping() {
        assertThat(policy().execute(() -> "ok")).isEqualTo("ok");
        assertThat(slept).isEmpty();
    }

    @Test
    void backsOffExponentiallyBetweenAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        policy().execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw AiProviderException.unavailable("provider");
            }
            return "ok";
        });

        assertThat(slept).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    /** Retrying a bad key three times only delays the error the operator needs to see. */
    @Test
    void doesNotRetryANonRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy().execute(() -> {
            attempts.incrementAndGet();
            throw AiProviderException.responseInvalid("schema");
        })).isInstanceOf(AiProviderException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    void rethrowsTheLastFailureOnceTheBudgetIsSpent() {
        assertThatThrownBy(() -> policy().execute(() -> {
            throw AiProviderException.rateLimited("provider");
        })).isInstanceOf(AiProviderException.class)
                .extracting("code").isEqualTo("ai_rate_limited");
    }

    @Test
    void honoursMaxAttemptsOfOneAsRetryDisabled() {
        settings.setMaxAttempts(1);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy().execute(() -> {
            attempts.incrementAndGet();
            throw AiProviderException.unavailable("provider");
        })).isInstanceOf(AiProviderException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // Circuit breaker
    // ---------------------------------------------------------------------------------------

    @Test
    void staysClosedBelowTheFailureThreshold() {
        CircuitBreakerGate breaker = breaker(Instant.parse("2026-07-28T00:00:00Z"));

        for (int failure = 0; failure < 4; failure++) {
            breaker.recordFailure("anthropic");
        }

        assertThat(breaker.allowRequest("anthropic")).isTrue();
    }

    @Test
    void opensAtTheThresholdAndOnlyForThatProvider() {
        CircuitBreakerGate breaker = breaker(Instant.parse("2026-07-28T00:00:00Z"));

        for (int failure = 0; failure < 5; failure++) {
            breaker.recordFailure("anthropic");
        }

        assertThat(breaker.allowRequest("anthropic")).isFalse();
        assertThat(breaker.allowRequest("openai")).isTrue();
    }

    @Test
    void closesOnASuccessBeforeTheThreshold() {
        CircuitBreakerGate breaker = breaker(Instant.parse("2026-07-28T00:00:00Z"));

        for (int failure = 0; failure < 4; failure++) {
            breaker.recordFailure("anthropic");
        }
        breaker.recordSuccess("anthropic");
        breaker.recordFailure("anthropic");

        assertThat(breaker.allowRequest("anthropic")).isTrue();
    }

    /** One probe after the cooldown, not the whole load — that is how a recovering provider survives. */
    @Test
    void admitsASingleProbeAfterTheCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        CircuitBreakerGate breaker = new CountingCircuitBreakerGate(settings, clock);
        for (int failure = 0; failure < 5; failure++) {
            breaker.recordFailure("anthropic");
        }

        assertThat(breaker.allowRequest("anthropic")).isFalse();
        clock.advance(settings.getCircuitBreakerOpenDuration().plusSeconds(1));

        assertThat(breaker.allowRequest("anthropic")).isTrue();
    }

    @Test
    void reopensImmediatelyWhenTheProbeFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        CircuitBreakerGate breaker = new CountingCircuitBreakerGate(settings, clock);
        for (int failure = 0; failure < 5; failure++) {
            breaker.recordFailure("anthropic");
        }
        clock.advance(settings.getCircuitBreakerOpenDuration().plusSeconds(1));
        breaker.allowRequest("anthropic");

        breaker.recordFailure("anthropic");

        assertThat(breaker.allowRequest("anthropic")).isFalse();
    }

    // ---------------------------------------------------------------------------------------

    private AiRetryPolicy policy() {
        return new AiRetryPolicy(settings, slept::add);
    }

    private CircuitBreakerGate breaker(Instant now) {
        return new CountingCircuitBreakerGate(settings, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** A clock a test can move, so a 30-second cooldown does not cost 30 seconds of build time. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
