package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Row mapping for {@code itinerary_item} (V27). Owned by {@link ItineraryDayEntity}.
 *
 * <p>{@code scheduledStart}/{@code scheduledEnd} are {@link LocalTime} against {@code time} columns,
 * not instants. A block is a wall-clock intent in the itinerary's zone; an instant would pin it to a
 * UTC offset and move the plan the next time that zone's DST rules changed.
 *
 * <p>{@code poiId} is a plain column rather than a {@code @ManyToOne}: the domain holds an id, the
 * FK is {@code ON DELETE SET NULL} so a withdrawn POI does not delete somebody's plan, and an
 * association here would invite a lazy load in the middle of rendering a timeline.
 */
@Entity
@Table(name = "itinerary_item")
public class ItineraryItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_day_id", nullable = false)
    private ItineraryDayEntity day;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "poi_id")
    private UUID poiId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ItineraryItemCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "scheduled_start", nullable = false)
    private LocalTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalTime scheduledEnd;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    @Column(name = "notes")
    private String notes;

    public ItineraryItemEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ItineraryDayEntity getDay() {
        return day;
    }

    public void setDay(ItineraryDayEntity day) {
        this.day = day;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public UUID getPoiId() {
        return poiId;
    }

    public void setPoiId(UUID poiId) {
        this.poiId = poiId;
    }

    public ItineraryItemCategory getCategory() {
        return category;
    }

    public void setCategory(ItineraryItemCategory category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalTime getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(LocalTime scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public LocalTime getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(LocalTime scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
