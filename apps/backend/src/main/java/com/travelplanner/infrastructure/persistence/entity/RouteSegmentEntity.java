package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code route_segment} (V16).
 *
 * <p>{@code estimated} is the honesty flag ADR 010 requires: curated legs exist only for the area
 * pairs somebody actually authored, and everything else is derived from mode heuristics. The column
 * defaults to {@code true} in the schema so a row inserted without thinking about it claims
 * <em>less</em>, not more.
 *
 * <p>{@code durationMinutes} stays an {@code int}, matching {@code integer NOT NULL}. The domain
 * uses {@link java.time.Duration} because a leg is a length of time rather than a number, but a
 * {@code Duration} field here would need a converter capable of representing sub-minute components
 * the column cannot store — the conversion belongs in the mapper, where the truncation is visible.
 *
 * <p>No {@code @Version}: the table has no {@code version} column.
 */
@Entity
@Table(name = "route_segment")
public class RouteSegmentEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "from_area_id", nullable = false)
    private UUID fromAreaId;

    @Column(name = "to_area_id", nullable = false)
    private UUID toAreaId;

    @Column(name = "transport_mode_id", nullable = false)
    private UUID transportModeId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    /** {@code text}. Curator commentary, e.g. which exit to use. Absent on most legs. */
    @Column(name = "notes")
    private String notes;

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
    public RouteSegmentEntity() {
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

    public UUID getFromAreaId() {
        return fromAreaId;
    }

    public void setFromAreaId(UUID fromAreaId) {
        this.fromAreaId = fromAreaId;
    }

    public UUID getToAreaId() {
        return toAreaId;
    }

    public void setToAreaId(UUID toAreaId) {
        this.toAreaId = toAreaId;
    }

    public UUID getTransportModeId() {
        return transportModeId;
    }

    public void setTransportModeId(UUID transportModeId) {
        this.transportModeId = transportModeId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public void setEstimated(boolean estimated) {
        this.estimated = estimated;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
