package com.travelplanner.application.itinerary;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.TripRepositoryPort;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * The one transactional write that moves a trip to {@code ITINERARY_READY} (task 30 DoD:
 * "successful persistence atomically transitions Trip status").
 *
 * <p>Its own bean, and a cross-bean call, for the reason F-37 recorded: {@code @TransactionalWrite}
 * is proxy-based, so a service calling its own annotated method gets no transaction at all. The
 * same split {@code DevAdminSeedWriter} and {@code ResearchCompletionMailWriter} use.
 *
 * <p>Deliberately short. Everything expensive — the model call, the guardrails, scheduling, route
 * resolution — has already finished by the time this opens a boundary.
 */
@Service
@RequiresDatabase
public class ItineraryStatusWriter {

    private final TripRepositoryPort trips;
    private final Clock clock = Clock.systemUTC();

    public ItineraryStatusWriter(TripRepositoryPort trips) {
        this.trips = trips;
    }

    /**
     * Moves the trip to {@code ITINERARY_READY}.
     *
     * <p>Idempotent by intent: a trip already there is left alone rather than re-saved, so a retry
     * after a partial failure does not burn an optimistic-lock version for no change.
     */
    @TransactionalWrite
    public Trip markItineraryReady(Trip trip) {
        if (trip.status() == TripStatus.ITINERARY_READY) {
            return trip;
        }
        return trips.save(trip.withStatus(TripStatus.ITINERARY_READY, clock.instant()));
    }
}
