package com.travelplanner.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * CI regression gate for research prompts/tools (task 27, BACKLOG S4-9).
 *
 * <p>Runs on every {@code ./gradlew test}. PLAN §15.3 also requires this when {@code ai/prompt/}
 * changes — including it in the unit suite satisfies that without a separate secrets-bearing job.
 */
class ResearchEvalHarnessTest {

    private static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "interests",
            "budget",
            "seasonality",
            "unsupported_data",
            "contradictory_sources",
            "missing_provenance",
            "tool_loops",
            "prompt_injection",
            "malformed_output",
            "no_result",
            "provider_timeout",
            "selection_policy");

    private final ResearchEvalHarness harness = ResearchEvalHarness.ciDefaults(new ObjectMapper());

    @Test
    void fixturesCoverEveryRequiredResearchCategory() {
        Set<String> categories = harness.loadCases().stream()
                .map(ResearchEvalCase::category)
                .collect(Collectors.toSet());
        assertThat(categories).containsAll(REQUIRED_CATEGORIES);
    }

    @Test
    void everyFixtureMatchesItsExpectedPassOutcomeFailClosed() {
        List<ResearchEvalCase> cases = harness.loadCases();
        assertThat(cases).isNotEmpty();
        assertThat(harness.reportFailures(cases)).isEmpty();
    }

    @Test
    void sourceCoverageIsOneWhenEveryRequiredRefIsClaimed() {
        assertThat(ResearchEvalHarness.sourceCoverage(
                List.of("a", "b"), List.of("b", "a"))).isEqualTo(1.0);
        assertThat(ResearchEvalHarness.sourceCoverage(
                List.of("a", "b"), List.of("a"))).isEqualTo(0.5);
    }

    @Test
    void metricsFailClosedOnUnsupportedClaimsEvenWhenCoverageIsPerfect() {
        ResearchEvalMetrics metrics = new ResearchEvalMetrics(true, 1.0, 1, 1, 1L, 0.0);
        assertThat(metrics.passes(ResearchEvalMetrics.Thresholds.ciDefaults())).isFalse();
    }
}
