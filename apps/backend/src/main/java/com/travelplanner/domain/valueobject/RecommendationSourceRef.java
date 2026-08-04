package com.travelplanner.domain.valueobject;

import java.util.Objects;
import java.util.Optional;

/**
 * A citation on a recommendation field (PLAN §4.1.0 provenance; UC-K05).
 *
 * <p>Either a TKB {@code knowledge_source.source_ref} (e.g. {@code stub:sample},
 * {@code wikivoyage:tokyo}) or a live supplement URL. Guardrails reject a ref the agent invented —
 * every value here must appear in the tool results that produced the narrative.
 *
 * @param sourceRef stable handle or absolute URL
 * @param sourceUrl optional display URL when the ref itself is not a URL
 * @param fieldGroup which traveler-guide / rationale section this citation supports
 */
public record RecommendationSourceRef(String sourceRef, String sourceUrl, String fieldGroup) {

    public RecommendationSourceRef {
        Objects.requireNonNull(sourceRef, "sourceRef");
        if (sourceRef.isBlank()) {
            throw new IllegalArgumentException("sourceRef must not be blank");
        }
        fieldGroup = fieldGroup == null || fieldGroup.isBlank() ? "general" : fieldGroup.trim();
        sourceUrl = blankToNull(sourceUrl);
    }

    public static RecommendationSourceRef of(String sourceRef, String fieldGroup) {
        return new RecommendationSourceRef(sourceRef, null, fieldGroup);
    }

    public static RecommendationSourceRef ofKnowledge(KnowledgeProvenance provenance, String fieldGroup) {
        Objects.requireNonNull(provenance, "provenance");
        return new RecommendationSourceRef(
                provenance.sourceRef(), provenance.sourceUrl(), fieldGroup);
    }

    public Optional<String> sourceUrlIfPresent() {
        return Optional.ofNullable(sourceUrl);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
