package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.LegResolution;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code itinerary_leg} (V28).
 *
 * <p>Not a child collection of {@link ItineraryDayEntity}, unlike items. A leg is written by task
 * 29's resolution pass <em>after</em> the plan exists, and making it part of the itinerary aggregate
 * would mean every save of a plan rewrote its legs — including the saves that happen before any
 * route has been resolved. The FK to the day is what keeps the cascade working on delete.
 *
 * <p>{@code recommendedAppIds} is a {@code uuid[]}, mapped with {@code JdbcTypeCode(ARRAY)} the way
 * {@code PoiEntity} maps {@code tags text[]}. It is written once with the leg and read whole, so a
 * join table would add a table to serve no query.
 */
@Entity
@Table(name = "itinerary_leg")
public class ItineraryLegEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "itinerary_day_id", nullable = false)
    private UUID itineraryDayId;

    @Column(name = "from_item_id", nullable = false)
    private UUID fromItemId;

    @Column(name = "to_item_id", nullable = false)
    private UUID toItemId;

    /** Null for an UNKNOWN leg — the curated vocabulary has no "unknown" member. */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", length = 32)
    private TransportKind transportMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = false, length = 32)
    private LegResolution resolution;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_band", length = 16)
    private PriceBand costBand;

    @Column(name = "route_segment_id")
    private UUID routeSegmentId;

    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    @Column(name = "instructions")
    private String instructions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recommended_app_ids", nullable = false)
    private UUID[] recommendedAppIds = new UUID[0];

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ItineraryLegEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getItineraryDayId() {
        return itineraryDayId;
    }

    public void setItineraryDayId(UUID itineraryDayId) {
        this.itineraryDayId = itineraryDayId;
    }

    public UUID getFromItemId() {
        return fromItemId;
    }

    public void setFromItemId(UUID fromItemId) {
        this.fromItemId = fromItemId;
    }

    public UUID getToItemId() {
        return toItemId;
    }

    public void setToItemId(UUID toItemId) {
        this.toItemId = toItemId;
    }

    public TransportKind getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(TransportKind transportMode) {
        this.transportMode = transportMode;
    }

    public LegResolution getResolution() {
        return resolution;
    }

    public void setResolution(LegResolution resolution) {
        this.resolution = resolution;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public PriceBand getCostBand() {
        return costBand;
    }

    public void setCostBand(PriceBand costBand) {
        this.costBand = costBand;
    }

    public UUID getRouteSegmentId() {
        return routeSegmentId;
    }

    public void setRouteSegmentId(UUID routeSegmentId) {
        this.routeSegmentId = routeSegmentId;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public UUID[] getRecommendedAppIds() {
        return recommendedAppIds;
    }

    public void setRecommendedAppIds(UUID[] recommendedAppIds) {
        this.recommendedAppIds = recommendedAppIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
