/**
 * Timeouts, bounded retry, and the circuit-breaker seam (PLAN §9 "Resilience").
 *
 * <p>Deliberately dependency-free. Resilience4j would be a new library, which
 * {@code docs/AGENT-HARNESS.md} §4 flags as an architecture change needing its own decision; what
 * task 14 requires is the extension point, so {@code CircuitBreakerGate} is an interface with a
 * counting implementation that a library-backed one can replace without touching a caller.
 */
package com.travelplanner.ai.resilience;
