package com.travelplanner.ai.eval;

import java.util.List;
import java.util.Objects;

/**
 * One deterministic research evaluation fixture (task 27 / S4-9).
 *
 * @param id stable case id used in CI failure messages
 * @param category interests|budget|seasonality|unsupported_data|contradictory_sources|
 *        missing_provenance|tool_loops|prompt_injection|malformed_output|no_result|
 *        provider_timeout|selection_policy
 * @param expectedPass whether a correctly grounded run should pass thresholds
 */
public record ResearchEvalCase(
        String id,
        String category,
        List<String> requiredSourceRefs,
        List<String> claimedSourceRefs,
        List<String> unsupportedClaims,
        int toolCallCount,
        long latencyMs,
        double estimatedCostUsd,
        boolean schemaValid,
        boolean expectedPass) {

    public ResearchEvalCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (id.isBlank() || category.isBlank()) {
            throw new IllegalArgumentException("id/category must not be blank");
        }
        requiredSourceRefs = requiredSourceRefs == null ? List.of() : List.copyOf(requiredSourceRefs);
        claimedSourceRefs = claimedSourceRefs == null ? List.of() : List.copyOf(claimedSourceRefs);
        unsupportedClaims = unsupportedClaims == null ? List.of() : List.copyOf(unsupportedClaims);
    }
}
