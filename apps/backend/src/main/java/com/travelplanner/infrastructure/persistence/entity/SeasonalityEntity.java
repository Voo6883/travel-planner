package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.WeatherBand;
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
 * Row mapping for {@code seasonality} (V17).
 *
 * <p>{@code month} is a {@code short} because the column is {@code smallint}. Hibernate maps a Java
 * {@code int} to {@code integer}, and {@code ddl-auto: validate} compares the two — so an
 * {@code int} field here would fail schema validation on startup rather than at the point of use.
 * The domain uses {@code int}, and the mapper widens; the widening is free and the alternative is a
 * mismatch that only appears in an environment with a real database.
 *
 * <p>All three bands are NOT NULL. A month answering only one of "what is the weather, how busy is
 * it, what does it cost" scores as partially-known in a sum that cannot tell "mild" from
 * "unrecorded" (PLAN §4.1.2).
 *
 * <p>No {@code @Version}: the table has no {@code version} column.
 */
@Entity
@Table(name = "seasonality")
public class SeasonalityEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    /** 1-12. A recurring shape, not an event — a date here would invite filtering by year. */
    @Column(name = "month", nullable = false)
    private short month;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_band", nullable = false, length = 16)
    private WeatherBand weatherBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "crowd_band", nullable = false, length = 16)
    private CrowdBand crowdBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_band", nullable = false, length = 16)
    private PriceBand priceBand;

    /** {@code text}. Curator commentary, e.g. a festival week that skews the crowd band. */
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
    public SeasonalityEntity() {
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

    public short getMonth() {
        return month;
    }

    public void setMonth(short month) {
        this.month = month;
    }

    public WeatherBand getWeatherBand() {
        return weatherBand;
    }

    public void setWeatherBand(WeatherBand weatherBand) {
        this.weatherBand = weatherBand;
    }

    public CrowdBand getCrowdBand() {
        return crowdBand;
    }

    public void setCrowdBand(CrowdBand crowdBand) {
        this.crowdBand = crowdBand;
    }

    public PriceBand getPriceBand() {
        return priceBand;
    }

    public void setPriceBand(PriceBand priceBand) {
        this.priceBand = priceBand;
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
