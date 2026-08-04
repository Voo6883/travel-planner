package com.travelplanner.domain.port;

import com.travelplanner.domain.model.ItineraryLeg;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link ItineraryLeg}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Separate from {@code ItineraryRepositoryPort} on purpose. Legs are resolved <em>after</em> a
 * plan exists — task 29 runs over an itinerary task 28 already wrote — so folding them into that
 * aggregate would mean every save of a plan rewrote its legs, including the saves that happen before
 * any route has been resolved.
 */
public interface ItineraryLegRepositoryPort {

    /**
     * Replaces every leg of a day.
     *
     * <p>Replace rather than append: re-resolving a day is how a plan picks up newly curated routes,
     * and appending would leave the old answer beside the new one with
     * {@code uq_itinerary_leg_pair} deciding which survived by accident.
     */
    List<ItineraryLeg> replaceForDay(UUID itineraryDayId, List<ItineraryLeg> legs);

    /** The day's legs, in the order the items they join appear (UC-C3-08). */
    List<ItineraryLeg> findByDayId(UUID itineraryDayId);

    /** Every leg of a plan, for a whole-itinerary read. */
    List<ItineraryLeg> findByDayIds(List<UUID> itineraryDayIds);
}
