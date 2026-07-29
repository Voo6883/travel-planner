package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code knowledge_source} (V13).
 *
 * <p>Every factual row in the TKB points here, so this is the one table whose absence would make a
 * fact unciteable. It has no domain record of its own: {@code KnowledgeProvenance} is what callers
 * receive, and it is assembled from this row plus the citing row's own {@code retrieved_at}.
 *
 * <p>{@code licence} and {@code trustTier} are {@code EnumType.STRING} against the V13 CHECK
 * constraints. Ordinal storage would tie the column to declaration order in
 * {@link KnowledgeLicence}, and reordering an enum is exactly the kind of harmless-looking edit that
 * would silently relabel every stored licence.
 *
 * <p>No {@code @Version}: the table has no {@code version} column. Sources are rewritten by the
 * seeder, not edited concurrently.
 */
@Entity
@Table(name = "knowledge_source")
public class KnowledgeSourceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** The stable handle seeds address sources by, so a re-seed need not preserve generated ids. */
    @Column(name = "source_ref", nullable = false, length = 160)
    private String sourceRef;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "licence", nullable = false, length = 64)
    private KnowledgeLicence licence;

    /** NOT NULL because a licence with attribution obligations and no string to display is unusable. */
    @Column(name = "attribution_text", nullable = false, length = 500)
    private String attributionText;

    /** Null only for the reserved stub source, which ADR 010 §3 forbids giving a plausible URL. */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /**
     * When the <em>source</em> was fetched.
     *
     * <p>Not the freshness of any row citing it. A source refreshed today does not make a guide
     * written against last year's fetch current, which is why each catalogue table denormalises its
     * own {@code retrieved_at} and why the provenance mapper reads that one instead of this.
     */
    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_tier", nullable = false, length = 32)
    private TrustTier trustTier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public KnowledgeSourceEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KnowledgeLicence getLicence() {
        return licence;
    }

    public void setLicence(KnowledgeLicence licence) {
        this.licence = licence;
    }

    public String getAttributionText() {
        return attributionText;
    }

    public void setAttributionText(String attributionText) {
        this.attributionText = attributionText;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public TrustTier getTrustTier() {
        return trustTier;
    }

    public void setTrustTier(TrustTier trustTier) {
        this.trustTier = trustTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
