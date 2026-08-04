package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.ItineraryStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Row mapping for {@code itinerary} (V27).
 *
 * <p><strong>The days are a mapped collection, unlike every other aggregate here.</strong> Elsewhere
 * this codebase keeps entities flat and lets the adapter assemble. A plan is the exception because
 * its invariants are cross-row — no empty day in a published plan, day numbers contiguous — so the
 * aggregate is only ever written whole, and {@code orphanRemoval} is what makes "write it whole"
 * actually replace rather than accumulate. A regenerated three-day plan must not leave days four and
 * five behind.
 *
 * <p>{@code version} is a primitive {@code int} for the reason {@link TripEntity} documents: Spring
 * Data only uses a version property for new-entity detection when it is nullable, so a primitive
 * keeps every {@code save} on the {@code merge} path.
 */
@Entity
@Table(name = "itinerary")
public class ItineraryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ItineraryStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Eager, and deliberately: a plan is never useful without its days, so the lazy version would be
     * an N+1 in every caller plus a {@code LazyInitializationException} the first time one reads
     * outside a transaction.
     */
    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("dayNumber ASC")
    private List<ItineraryDayEntity> days = new ArrayList<>();

    public ItineraryEntity() {
    }

    /** Keeps both sides of the association in step; JPA maintains neither for you. */
    public void replaceDays(List<ItineraryDayEntity> replacements) {
        days.clear();
        for (ItineraryDayEntity day : replacements) {
            day.setItinerary(this);
            days.add(day);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public ItineraryStatus getStatus() {
        return status;
    }

    public void setStatus(ItineraryStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
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

    public List<ItineraryDayEntity> getDays() {
        return days;
    }

    public void setDays(List<ItineraryDayEntity> days) {
        this.days = days;
    }
}
