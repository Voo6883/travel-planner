package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.UUID;

/**
 * One hit from hybrid retrieval over the TKB (ADR 010 §5).
 *
 * <p><strong>A match is not just a row id.</strong> ADR 010 §5 requires it to carry both the score
 * and the source reference, and the reason is that the two answer questions nobody else can. The
 * score is how the caller applies the similarity floor and orders a rerank; the provenance is how
 * the agent cites what it retrieved, which PLAN §4.1.0 makes non-negotiable. A match that reached
 * the agent without them would have to be re-joined to two tables before it could be used, and the
 * step that gets skipped is always the citation.
 *
 * <p>This is a read projection, not an aggregate: it is assembled per query and never persisted, so
 * {@code snippet} is a copy of the matched text rather than a pointer into the row it came from.
 *
 * @param sourceType which table {@code id} refers to. {@code id} alone is ambiguous across the two
 *        embedded tables, and resolving a guide id against {@code poi} finds nothing rather than
 *        failing
 * @param score cosine similarity in {@code 0.0..1.0}. ADR 010 §5 sets the retrieval floor at
 *        {@code 0.5}, which is applied by the caller — the floor is a tuning decision and a record
 *        that enforced it could not be used to explain why something was dropped
 */
public record KnowledgeMatch(
        KnowledgeMatchType sourceType,
        UUID id,
        UUID destinationId,
        String snippet,
        double score,
        KnowledgeProvenance provenance) {

    public KnowledgeMatch {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(snippet, "snippet");
        Objects.requireNonNull(provenance, "provenance");

        if (snippet.isBlank()) {
            throw new IllegalArgumentException("snippet must not be blank");
        }
        // NaN is tested first: every comparison against NaN is false, so the range check below
        // would wave it through and it would then sort unpredictably against real scores.
        if (Double.isNaN(score)) {
            throw new IllegalArgumentException("score must be a number, got NaN");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be 0.0..1.0, got " + score);
        }
    }
}
