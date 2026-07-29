package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code destination_area} (V14).
 *
 * <p>The neighbourhood level C3 schedules against, and the endpoints a curated
 * {@link RouteSegmentEntity} connects. {@code slug} is unique within a destination rather than
 * globally, matching {@code uq_destination_area_destination_slug} — "downtown" is a reasonable area
 * name in more than one city.
 *
 * <p>Coordinates are {@link BigDecimal} for the reason {@link DestinationEntity} documents: a
 * {@code double} field would silently change the scale of a {@code numeric(9,6)} column.
 *
 * <p>No {@code @Version}: the table has no {@code version} column.
 */
@Entity
@Table(name = "destination_area")
public class DestinationAreaEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** {@code text}. Null while the area is a placeholder the curator has not written up yet. */
    @Column(name = "description")
    private String description;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

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
    public DestinationAreaEntity() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
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
