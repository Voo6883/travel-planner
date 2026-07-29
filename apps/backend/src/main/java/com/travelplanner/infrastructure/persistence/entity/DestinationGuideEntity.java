package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code destination_guide} (V14).
 *
 * <p>{@code overview}, {@code food} and {@code practical} are three columns rather than one blob
 * because ADR 010 §5 embeds them separately; merging them here would quietly undo that decision one
 * layer above the schema. Only {@code overview} is NOT NULL — an absent section is honest, an empty
 * string is not, and a blank overview embeds to a vector of nothing that retrieves as noise.
 *
 * <p>{@code destinationId} is a raw {@link UUID} rather than an association. The domain record holds
 * the same raw id, so an association would exist only to be dereferenced back into one, at the cost
 * of a lazy proxy the mapper would have to touch. {@link #source} is the exception: the mapper reads
 * the licence and attribution off it to build {@code KnowledgeProvenance}, so the join has to be
 * there — see {@code DestinationGuideJpaRepository} for why every list query fetches it eagerly.
 *
 * <p>{@code version} is a primitive {@code int} for the reason {@code TripEntity} documents: Spring
 * Data only uses a version property for new-entity detection when it is nullable, so a primitive
 * keeps {@code save} consistently on the {@code merge} path instead of alternating with
 * {@code persist} depending on field state. It is a curation counter (task 41), not an ADR 008
 * optimistic lock — but it is still declared {@code @Version} so two curators saving the same guide
 * cannot silently overwrite one another.
 */
@Entity
@Table(name = "destination_guide")
public class DestinationGuideEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    /** ADR 010 §5: {@code en} is authoritative for v1 embeddings; other locales are display only. */
    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    /** {@code text}. No {@code length}: the column has none, and guides run to several paragraphs. */
    @Column(name = "overview", nullable = false)
    private String overview;

    @Column(name = "food")
    private String food;

    @Column(name = "practical")
    private String practical;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    /** This guide's own fetch time — ADR 010 §6 measures the 730-day narrative TTL against it. */
    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public DestinationGuideEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getFood() {
        return food;
    }

    public void setFood(String food) {
        this.food = food;
    }

    public String getPractical() {
        return practical;
    }

    public void setPractical(String practical) {
        this.practical = practical;
    }

    @Override
    public KnowledgeSourceEntity getSource() {
        return source;
    }

    public void setSource(KnowledgeSourceEntity source) {
        this.source = source;
    }

    @Override
    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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
