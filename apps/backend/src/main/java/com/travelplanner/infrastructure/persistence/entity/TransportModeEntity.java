package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code transport_mode} (V16).
 *
 * <p>Modes are per destination rather than global because {@code kind} alone does not tell C3 what
 * it needs: "metro" means a tourist-legible network in one city and something else in the next.
 * {@code touristFriendly} is the column carrying that judgement — the sort of fact a guide states
 * and a model otherwise guesses — so it is a primitive {@code boolean} against a NOT NULL column,
 * not a {@link Boolean} that could arrive as "unknown".
 *
 * <p>No {@code @Version}: the table has no {@code version} column.
 */
@Entity
@Table(name = "transport_mode")
public class TransportModeEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private TransportKind kind;

    /** {@code text}. Null when the curator recorded the mode but not how it works. */
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_band", length = 16)
    private PriceBand costBand;

    @Column(name = "tourist_friendly", nullable = false)
    private boolean touristFriendly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public TransportModeEntity() {
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TransportKind getKind() {
        return kind;
    }

    public void setKind(TransportKind kind) {
        this.kind = kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PriceBand getCostBand() {
        return costBand;
    }

    public void setCostBand(PriceBand costBand) {
        this.costBand = costBand;
    }

    public boolean isTouristFriendly() {
        return touristFriendly;
    }

    public void setTouristFriendly(boolean touristFriendly) {
        this.touristFriendly = touristFriendly;
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
