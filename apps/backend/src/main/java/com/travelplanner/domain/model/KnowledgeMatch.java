package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.UUID;

/**
 * One hit from hybrid retrieval over the TKB (ADR 010 §5).
 *
 * <p><strong>A match is not just a row id.</strong> ADR 010 §5 requires it to carry both a score and
 * the source reference, and the reason is that the two answer questions nobody else can. The score is
 * how the caller orders a rerank; the provenance is how the agent cites what it retrieved, which PLAN
 * §4.1.0 makes non-negotiable. A match that reached the agent without them would have to be re-joined
 * to two tables before it could be used, and the step that gets skipped is always the citation.
 *
 * <p>This is a read projection, not an aggregate: it is assembled per query and never persisted, so
 * {@code snippet} is a copy of the matched text rather than a pointer into the row it came from.
 *
 * <h2>{@code relevance} is a rank, not a similarity</h2>
 *
 * <p>This field used to be called {@code score} and used to be a cosine similarity. It is neither now,
 * and the rename is deliberate — a silent change of meaning on a field named {@code score} is how a
 * caller ends up filtering on a threshold that no longer means anything.
 *
 * <p>Retrieval fuses two arms whose numbers are not on the same scale: cosine similarity is bounded
 * and roughly calibrated across queries, {@code ts_rank} is unbounded and is not. They cannot be added
 * together, so {@code KnowledgeHybridSearch} discards both and fuses on each arm's <em>ordering</em>
 * instead (reciprocal rank fusion). {@code relevance} is the normalised result: 1.0 means first place
 * in every arm consulted, 0.5 means first place in one arm and absent from the other.
 *
 * <p>Three consequences worth stating, because each is a plausible mistake:
 *
 * <ul>
 *   <li><strong>Do not compare it with {@link
 *       com.travelplanner.domain.valueobject.KnowledgeQuery#similarityFloor()}.</strong> That floor is
 *       a cosine floor, applied inside the vector arm's SQL. Applied to a fused relevance it would
 *       drop every single-arm match — and a single-arm match is the only kind hybrid retrieval can add
 *       over pure vector, so the filter would quietly undo the fusion it was meant to clean up.</li>
 *   <li><strong>It is comparable within one result list, not across queries.</strong> Rank is
 *       relative to what else that query returned.</li>
 *   <li><strong>It says nothing about whether the row is <em>true</em>.</strong> Freshness lives in
 *       {@link KnowledgeProvenance#retrievedAt()} and trust in its {@code trustTier}; ADR 010 §6
 *       requires a consumer to downgrade confidence on a stale row regardless of how well it
 *       ranked.</li>
 * </ul>
 *
 * @param sourceType which table {@code id} refers to. {@code id} alone is ambiguous across the two
 *        embedded tables, and resolving a guide id against {@code poi} finds nothing rather than
 *        failing
 * @param relevance fused rank position in {@code 0.0..1.0}, higher is better. See the note above
 */
public record KnowledgeMatch(
        KnowledgeMatchType sourceType,
        UUID id,
        UUID destinationId,
        String snippet,
        double relevance,
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
        // would wave it through and it would then sort unpredictably against real values.
        if (Double.isNaN(relevance)) {
            throw new IllegalArgumentException("relevance must be a number, got NaN");
        }
        if (relevance < 0.0 || relevance > 1.0) {
            throw new IllegalArgumentException("relevance must be 0.0..1.0, got " + relevance);
        }
    }
}
