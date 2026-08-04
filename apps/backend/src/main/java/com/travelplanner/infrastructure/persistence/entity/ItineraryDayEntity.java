package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Row mapping for {@code itinerary_day} (V27). Owned by {@link ItineraryEntity}. */
@Entity
@Table(name = "itinerary_day")
public class ItineraryDayEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private ItineraryEntity itinerary;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "window_start", nullable = false)
    private LocalTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalTime windowEnd;

    /** Ordered by ordinal, which is gapless and matches clock order — see {@code ItineraryDay}. */
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<ItineraryItemEntity> items = new ArrayList<>();

    public ItineraryDayEntity() {
    }

    public void replaceItems(List<ItineraryItemEntity> replacements) {
        items.clear();
        for (ItineraryItemEntity item : replacements) {
            item.setDay(this);
            items.add(item);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ItineraryEntity getItinerary() {
        return itinerary;
    }

    public void setItinerary(ItineraryEntity itinerary) {
        this.itinerary = itinerary;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public void setDayDate(LocalDate dayDate) {
        this.dayDate = dayDate;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public List<ItineraryItemEntity> getItems() {
        return items;
    }

    public void setItems(List<ItineraryItemEntity> items) {
        this.items = items;
    }
}
