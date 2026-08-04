package com.travelplanner.ai.eval;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated metrics for one research eval case (task 27 / BACKLOG S4-9).
 *
 * <p>Fail-closed: any metric below its threshold fails the case. Live provider evaluation is
 * separately approved — this harness scores deterministic fixtures only.
 */
public record ResearchEvalMetrics(
        boolean schemaValid,
        double sourceCoverage,
        int unsupportedClaimCount,
        int toolCallCount,
        long latencyMs,
        double estimatedCostUsd) {

    public ResearchEvalMetrics {
        if (sourceCoverage < 0.0 || sourceCoverage > 1.0) {
            throw new IllegalArgumentException("sourceCoverage must be in [0,1]");
        }
        if (unsupportedClaimCount < 0 || toolCallCount < 0 || latencyMs < 0) {
            throw new IllegalArgumentException("counts/latency must not be negative");
        }
        if (estimatedCostUsd < 0.0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
    }

    /** Fail-closed thresholds for CI regression (deterministic fixtures). */
    public record Thresholds(
            double minSourceCoverage,
            int maxUnsupportedClaims,
            int maxToolCalls,
            long maxLatencyMs,
            double maxEstimatedCostUsd) {

        public static Thresholds ciDefaults() {
            return new Thresholds(1.0, 0, 40, 5_000L, 0.0);
        }
    }

    public boolean passes(Thresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        return schemaValid
                && sourceCoverage >= thresholds.minSourceCoverage()
                && unsupportedClaimCount <= thresholds.maxUnsupportedClaims()
                && toolCallCount <= thresholds.maxToolCalls()
                && latencyMs <= thresholds.maxLatencyMs()
                && estimatedCostUsd <= thresholds.maxEstimatedCostUsd();
    }

    public List<String> failures(Thresholds thresholds) {
        List<String> out = new java.util.ArrayList<>();
        if (!schemaValid) {
            out.add("schema_valid=false");
        }
        if (sourceCoverage < thresholds.minSourceCoverage()) {
            out.add("source_coverage=" + sourceCoverage);
        }
        if (unsupportedClaimCount > thresholds.maxUnsupportedClaims()) {
            out.add("unsupported_claims=" + unsupportedClaimCount);
        }
        if (toolCallCount > thresholds.maxToolCalls()) {
            out.add("tool_calls=" + toolCallCount);
        }
        if (latencyMs > thresholds.maxLatencyMs()) {
            out.add("latency_ms=" + latencyMs);
        }
        if (estimatedCostUsd > thresholds.maxEstimatedCostUsd()) {
            out.add("estimated_cost_usd=" + estimatedCostUsd);
        }
        return List.copyOf(out);
    }
}
