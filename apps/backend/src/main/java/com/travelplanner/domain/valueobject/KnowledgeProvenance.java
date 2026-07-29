package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.enums.KnowledgeDataClass;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import java.time.Instant;
import java.util.Objects;

/**
 * Where a fact came from, and whether it can still be trusted (PLAN §4.1.0, ADR 010 §2 and §6).
 *
 * <p>Every factual record in the TKB carries one. That is the schema's rule — {@code source_id} is
 * {@code NOT NULL} on every catalogue table — and this type is what makes it usable above the
 * database: a caller holding a fact also holds the citation it must display and the age it must
 * reason about.
 *
 * <p><strong>Staleness is computed, not stored.</strong> A boolean column would be wrong the moment
 * after it was written and would need a job to keep it honest. {@link #isStaleAt(Instant,
 * KnowledgeDataClass)} takes the clock as an argument for the same reason the rest of the domain
 * does: a type that reads {@code Instant.now()} internally cannot be tested for the day before
 * expiry without waiting for it.
 *
 * @param sourceRef the stable handle a seed file uses, e.g. {@code wikivoyage:tokyo} — or the
 *        reserved {@code stub:sample}, which ADR 010 §3 requires instead of a plausible URL
 * @param sourceUrl absent for sample data, which deliberately has nowhere real to point
 * @param retrievedAt when the fact was taken from the source. Distinct from the row's creation
 *        time: a row imported today may describe a page fetched last month, and the TTL runs from
 *        the fetch
 */
public record KnowledgeProvenance(
        String sourceRef,
        String name,
        KnowledgeLicence licence,
        String attributionText,
        String sourceUrl,
        TrustTier trustTier,
        Instant retrievedAt) {

    /** ADR 010 §3: the one permitted citation for sample rows. */
    public static final String SAMPLE_SOURCE_REF = "stub:sample";

    public KnowledgeProvenance {
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(licence, "licence");
        Objects.requireNonNull(attributionText, "attributionText");
        Objects.requireNonNull(trustTier, "trustTier");
        Objects.requireNonNull(retrievedAt, "retrievedAt");

        if (sourceRef.isBlank()) {
            throw new IllegalArgumentException("sourceRef must not be blank");
        }
        // A licence requiring attribution with nothing to attribute is unusable: the UI has an
        // obligation and no string to satisfy it with.
        if (licence.requiresAttribution() && attributionText.isBlank()) {
            throw new IllegalArgumentException(
                    "licence " + licence + " requires attribution, but attributionText is blank");
        }
        // ADR 010 §2. The database enforces this too; failing here means a caller never gets far
        // enough to be surprised by a constraint violation.
        if (!licence.isPersistable()) {
            throw new IllegalArgumentException(
                    "licence " + licence + " may not be persisted in the TKB (ADR 010 §2)");
        }
        // Sample data and the SAMPLE tier imply each other, matching
        // ck_knowledge_source_sample_alignment. Without it a stub row could claim COMMUNITY trust
        // and become indistinguishable from curated content downstream.
        if ((licence == KnowledgeLicence.SAMPLE_DATA) != (trustTier == TrustTier.SAMPLE)) {
            throw new IllegalArgumentException(
                    "SAMPLE_DATA and TrustTier.SAMPLE must be used together, got "
                            + licence + " and " + trustTier);
        }
    }

    /** ADR 010 §3: sample content must be flagged wherever it is shown. */
    public boolean isSampleData() {
        return trustTier == TrustTier.SAMPLE;
    }

    /**
     * Whether this fact has outlived its class's TTL as of {@code now}.
     *
     * <p>A stale fact is still returned — it is the confidence that drops, not the row.
     */
    public boolean isStaleAt(Instant now, KnowledgeDataClass dataClass) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(dataClass, "dataClass");
        return retrievedAt.plus(dataClass.timeToLive()).isBefore(now);
    }
}
