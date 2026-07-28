package com.travelplanner.ai.resilience;

/**
 * The circuit-breaker extension point (task 14 Scope; PLAN §9 "circuit breaker per provider").
 *
 * <p>An interface plus a counting default implementation, rather than Resilience4j. Adding a
 * resilience library is an architecture change that the brief does not authorise and no ADR covers
 * ({@code docs/AGENT-HARNESS.md} §4: "about to add a dependency not in the brief"). What the brief
 * does require is the <em>seam</em> — so the interface is defined, the platform calls through it,
 * and swapping in Resilience4j later is one new implementation and one bean, with no caller change.
 *
 * <p>Keyed by provider name, because the breaker's whole purpose is to fail one provider without
 * failing the other: when Anthropic is down and OpenAI is not, routing must still work.
 */
public interface CircuitBreakerGate {

    /**
     * @return {@code false} when calls to {@code provider} should be rejected without being
     *     attempted. A caller that gets {@code false} must fail with {@code ai_unavailable} rather
     *     than falling back silently — a fallback nobody can see is how a degraded provider stays
     *     degraded for a week.
     */
    boolean allowRequest(String provider);

    void recordSuccess(String provider);

    void recordFailure(String provider);
}
