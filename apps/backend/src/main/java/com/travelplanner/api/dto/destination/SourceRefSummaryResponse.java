package com.travelplanner.api.dto.destination;

import com.travelplanner.domain.enums.KnowledgeDataClass;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Instant;

/**
 * One citation, with the two facts that decide how much it may be trusted (ADR 010 §2, §3, §6).
 *
 * <p><strong>Why the flags travel with the ref rather than the response.</strong> A guide answer is
 * assembled from rows curated at different times by different sources: the narrative may be two
 * years old and the POIs beside it fresh, or the whole page real except one sample app pack. A
 * single page-level "this might be old" cannot say which sentence it applies to, so a reader either
 * distrusts everything or ignores the warning. Per-ref makes the claim specific, which is the only
 * form of hedge a traveller can act on.
 *
 * <p><strong>{@code attribution} is a product obligation, not a footnote.</strong> ADR 010 §2 says
 * share-alike terms propagate into derived guide text, and {@link KnowledgeProvenance} already
 * refuses to exist when a licence requiring attribution has none. Publishing it here is what lets
 * the UI discharge the obligation; keeping it server-side satisfied the constraint in the database
 * and broke it on the screen, which is the half that a licensor actually reads.
 *
 * @param sourceRef the stable citation handle. {@code stub:sample} is the reserved sample ref
 * @param sourceUrl null when the source is not a fetchable document
 * @param fieldGroup which part of the answer this ref backs — {@code overview}, {@code food},
 *        {@code practical}, {@code areas}, {@code pois}, {@code transport}, {@code local_app_pack}
 * @param attribution null when the licence requires none; never blank when it does
 * @param sampleData ADR 010 §3 — this ref cites the sample seed and backs no real-world fact
 * @param stale the row has outlived its {@link KnowledgeDataClass} TTL. It is still returned: the
 *        fact is hedged, not hidden, because a hedged answer beats no answer
 * @param retrievedAt when the source was fetched, so a client can say "as of" rather than implying
 *        now
 */
public record SourceRefSummaryResponse(
        String sourceRef,
        String sourceUrl,
        String fieldGroup,
        String attribution,
        boolean sampleData,
        boolean stale,
        Instant retrievedAt) {

    /**
     * Builds a ref from the provenance that travels with the row it cites.
     *
     * @param dataClass the row's own TTL class, taken from the domain record rather than guessed
     *        here — a boundary that picks the wrong TTL reports the wrong staleness
     */
    public static SourceRefSummaryResponse from(
            KnowledgeProvenance provenance, String fieldGroup, KnowledgeDataClass dataClass,
            Instant now) {
        return new SourceRefSummaryResponse(
                provenance.sourceRef(),
                provenance.sourceUrl(),
                fieldGroup,
                provenance.licence().requiresAttribution() ? provenance.attributionText() : null,
                provenance.isSampleData(),
                provenance.isStaleAt(now, dataClass),
                provenance.retrievedAt());
    }
}
