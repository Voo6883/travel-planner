package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryDay;
import com.travelplanner.domain.model.ItineraryItem;
import com.travelplanner.infrastructure.persistence.entity.ItineraryDayEntity;
import com.travelplanner.infrastructure.persistence.entity.ItineraryEntity;
import com.travelplanner.infrastructure.persistence.entity.ItineraryItemEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code itinerary} ⇄ {@link Itinerary}, by hand rather than by MapStruct.
 *
 * <p>MapStruct maps fields; this has to maintain two bidirectional associations and re-derive an
 * ordinal per item. A generated mapper would produce days with a null parent — which persists as a
 * null FK violation at flush time, several frames from the cause — so the assembly is explicit.
 *
 * <p><strong>Reading rebuilds the domain objects, so every invariant is re-checked on the way
 * out.</strong> A row edited by hand into an overlapping day fails here rather than reaching a
 * traveller, which is the property {@code ddl-auto: validate} cannot give.
 */
@Component
public class ItineraryPersistenceMapper {

    /** Domain → entity. The caller supplies {@code now} so one save stamps one timestamp. */
    public ItineraryEntity toEntity(Itinerary itinerary, ItineraryEntity existing, Instant now) {
        ItineraryEntity entity = existing == null ? new ItineraryEntity() : existing;
        entity.setId(itinerary.id());
        entity.setTripId(itinerary.tripId());
        entity.setUserId(itinerary.userId());
        entity.setDestinationId(itinerary.destinationId());
        entity.setStatus(itinerary.status());
        entity.setStartDate(itinerary.startDate());
        entity.setEndDate(itinerary.endDate());
        entity.setTimezone(itinerary.timezone());
        entity.setAlgorithmVersion(itinerary.algorithmVersion());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        entity.replaceDays(itinerary.days().stream().map(this::toDayEntity).toList());
        return entity;
    }

    private ItineraryDayEntity toDayEntity(ItineraryDay day) {
        ItineraryDayEntity entity = new ItineraryDayEntity();
        entity.setId(day.id());
        entity.setDayNumber(day.dayNumber());
        entity.setDayDate(day.date());
        entity.setAreaId(day.areaId());
        entity.setWindowStart(day.windowStart());
        entity.setWindowEnd(day.windowEnd());
        entity.replaceItems(day.items().stream().map(this::toItemEntity).toList());
        return entity;
    }

    private ItineraryItemEntity toItemEntity(ItineraryItem item) {
        ItineraryItemEntity entity = new ItineraryItemEntity();
        entity.setId(item.id());
        entity.setOrdinal(item.ordinal());
        entity.setPoiId(item.poiId());
        entity.setCategory(item.category());
        entity.setTitle(item.title());
        entity.setScheduledStart(item.startsAt());
        entity.setScheduledEnd(item.endsAt());
        entity.setDurationMinutes(item.durationMinutes());
        entity.setSourceRef(item.sourceRef());
        entity.setNotes(item.notes());
        return entity;
    }

    /** Entity → domain. Every constructor invariant runs again here, by design. */
    public Itinerary toDomain(ItineraryEntity entity) {
        List<ItineraryDay> days = entity.getDays().stream().map(this::toDayDomain).toList();
        return new Itinerary(entity.getId(), entity.getTripId(), entity.getUserId(),
                entity.getDestinationId(), entity.getStatus(), entity.getStartDate(),
                entity.getEndDate(), entity.getTimezone(), entity.getAlgorithmVersion(), days,
                entity.getVersion());
    }

    private ItineraryDay toDayDomain(ItineraryDayEntity entity) {
        List<ItineraryItem> items = entity.getItems().stream().map(this::toItemDomain).toList();
        return new ItineraryDay(entity.getId(), entity.getDayNumber(), entity.getDayDate(),
                entity.getAreaId(), entity.getWindowStart(), entity.getWindowEnd(), items);
    }

    private ItineraryItem toItemDomain(ItineraryItemEntity entity) {
        return new ItineraryItem(entity.getId(), entity.getOrdinal(), entity.getPoiId(),
                entity.getCategory(), entity.getTitle(), entity.getScheduledStart(),
                entity.getScheduledEnd(), entity.getDurationMinutes(), entity.getSourceRef(),
                entity.getNotes());
    }
}
