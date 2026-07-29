package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import java.time.Instant;
import java.util.List;

/**
 * Parsed shape of {@code knowledge/sample/sources.json}.
 *
 * <p>A list rather than a single object, even though the sample set has exactly one entry, so that
 * tasks 40 and 41 can add real sources without a format change. What the reader enforces is that
 * every entry present is the reserved sample source — a second, plausible-looking source in this
 * file would be fabricated provenance, which ADR 010 §3 forbids outright.
 *
 * @param formatVersion bumped when the file shape changes incompatibly
 */
public record SampleSourceDocument(int formatVersion, List<SourceNode> sources) {

    public SampleSourceDocument {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /**
     * One row of {@code knowledge_source} (V13).
     *
     * @param sourceRef must be {@code stub:sample} for every sample row
     * @param sourceUrl must be {@code null}: sample data deliberately has nowhere real to point
     * @param retrievedAt {@code null} in the sample files, because nothing was retrieved — the
     *     reader substitutes the seed instant so ADR 010 §6's TTLs run from a real moment
     */
    public record SourceNode(
            String sourceRef,
            String name,
            KnowledgeLicence licence,
            String attributionText,
            String sourceUrl,
            Instant retrievedAt,
            TrustTier trustTier) {
    }
}
