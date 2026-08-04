package com.travelplanner.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic research prompt/grounding regression gate (task 27, BACKLOG S4-9).
 *
 * <p>Scores fixtures under {@code /ai/eval/}. Fail-closed: missing provenance, invented citations,
 * schema invalidity, or budget overruns fail the case. Stub cost is always {@code 0.0}. Live
 * evaluation remains separately approved.
 */
public final class ResearchEvalHarness {

    private static final String FIXTURE_INDEX = "/ai/eval/cases.json";

    private final ObjectMapper mapper;
    private final ResearchEvalMetrics.Thresholds thresholds;

    public ResearchEvalHarness(ObjectMapper mapper, ResearchEvalMetrics.Thresholds thresholds) {
        this.mapper = mapper;
        this.thresholds = thresholds;
    }

    public static ResearchEvalHarness ciDefaults(ObjectMapper mapper) {
        return new ResearchEvalHarness(mapper, ResearchEvalMetrics.Thresholds.ciDefaults());
    }

    public List<ResearchEvalCase> loadCases() {
        try (InputStream in = ResearchEvalHarness.class.getResourceAsStream(FIXTURE_INDEX)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + FIXTURE_INDEX);
            }
            JsonNode root = mapper.readTree(in);
            List<ResearchEvalCase> cases = new ArrayList<>();
            for (JsonNode node : root.withArray("cases")) {
                cases.add(parseCase(node));
            }
            return List.copyOf(cases);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load research eval fixtures", failure);
        }
    }

    public ResearchEvalMetrics score(ResearchEvalCase evalCase) {
        double coverage = sourceCoverage(evalCase.requiredSourceRefs(), evalCase.claimedSourceRefs());
        return new ResearchEvalMetrics(
                evalCase.schemaValid(),
                coverage,
                evalCase.unsupportedClaims().size(),
                evalCase.toolCallCount(),
                evalCase.latencyMs(),
                evalCase.estimatedCostUsd());
    }

    public boolean passes(ResearchEvalCase evalCase) {
        return score(evalCase).passes(thresholds) == evalCase.expectedPass();
    }

    public List<String> reportFailures(List<ResearchEvalCase> cases) {
        List<String> failures = new ArrayList<>();
        for (ResearchEvalCase evalCase : cases) {
            ResearchEvalMetrics metrics = score(evalCase);
            boolean actualPass = metrics.passes(thresholds);
            if (actualPass != evalCase.expectedPass()) {
                failures.add(evalCase.id() + " category=" + evalCase.category()
                        + " expectedPass=" + evalCase.expectedPass()
                        + " actualPass=" + actualPass
                        + " details=" + metrics.failures(thresholds));
            }
        }
        return List.copyOf(failures);
    }

    static double sourceCoverage(List<String> required, List<String> claimed) {
        if (required.isEmpty()) {
            return 1.0;
        }
        Set<String> claimedSet = new HashSet<>();
        for (String ref : claimed) {
            claimedSet.add(normalise(ref));
        }
        int hit = 0;
        for (String ref : required) {
            if (claimedSet.contains(normalise(ref))) {
                hit++;
            }
        }
        return (double) hit / (double) required.size();
    }

    private static String normalise(String ref) {
        return ref.trim().toLowerCase(Locale.ROOT);
    }

    private static ResearchEvalCase parseCase(JsonNode node) {
        return new ResearchEvalCase(
                text(node, "id"),
                text(node, "category"),
                texts(node, "required_source_refs"),
                texts(node, "claimed_source_refs"),
                texts(node, "unsupported_claims"),
                node.path("tool_call_count").asInt(),
                node.path("latency_ms").asLong(),
                node.path("estimated_cost_usd").asDouble(0.0),
                node.path("schema_valid").asBoolean(true),
                node.path("expected_pass").asBoolean(true));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }

    private static List<String> texts(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode child : node.withArray(field)) {
            values.add(child.asText());
        }
        return values;
    }
}
