package com.travelplanner.domain;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.time.Instant;

/**
 * Shared valid values for the travel-knowledge domain tests.
 *
 * <p>Every catalogue record carries a {@link KnowledgeProvenance}, so without a fixture each test
 * would open with seven lines of source metadata it does not care about — and the rule under test
 * would be the hardest line in the method to find.
 *
 * <p>The instants are fixed rather than derived from {@code Instant.now()}: a TTL boundary test
 * that moves with the wall clock is a test that can only fail on somebody else's machine.
 */
public final class KnowledgeFixtures {

    /** The moment every fixture treats as "when the fact was fetched". */
    public static final Instant RETRIEVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private KnowledgeFixtures() {
    }

    /** A community-tier Wikivoyage source: attribution required, share-alike, persistable. */
    public static KnowledgeProvenance provenance() {
        return new KnowledgeProvenance(
                "wikivoyage:tokyo",
                "Wikivoyage",
                KnowledgeLicence.CC_BY_SA_4_0,
                "Wikivoyage contributors, CC BY-SA 4.0",
                "https://en.wikivoyage.org/wiki/Tokyo",
                TrustTier.COMMUNITY,
                RETRIEVED_AT);
    }

    /** The reserved sample-row shape from ADR 010 §3: stub reference, no URL, SAMPLE tier. */
    public static KnowledgeProvenance sampleProvenance() {
        return new KnowledgeProvenance(
                KnowledgeProvenance.SAMPLE_SOURCE_REF,
                "Sample data",
                KnowledgeLicence.SAMPLE_DATA,
                "Sample data — not a real source",
                null,
                TrustTier.SAMPLE,
                RETRIEVED_AT);
    }

    /** A query vector of the pinned dimension, with a recognisable value in the first slot. */
    public static float[] embedding() {
        float[] embedding = new float[KnowledgeQuery.EMBEDDING_DIMENSION];
        embedding[0] = 0.25f;
        return embedding;
    }
}
