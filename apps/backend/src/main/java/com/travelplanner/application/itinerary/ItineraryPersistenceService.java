package com.travelplanner.application.itinerary;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.algorithm.scheduling.DayPlan;
import com.travelplanner.domain.algorithm.scheduling.SchedulingResult;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.port.ItineraryRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and reads the deterministic plan (task 28: "persistence service using short transactions
 * and conflict handling").
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>It does not schedule. {@code domain/algorithm/scheduling/} decides feasibility and this writes
 * the answer down — so the rule that a plan must be walkable lives in one pure, testable place
 * rather than being re-litigated by whoever is holding a transaction.
 *
 * <p>It does not call an LLM or a route provider. Task 30 narrates a plan that already exists and
 * task 29 fills in legs; both consume what is written here.
 *
 * <h2>Short transactions</h2>
 *
 * <p>The write is one statement's worth of work against an already-assembled aggregate. Scheduling
 * happens before the boundary opens, because holding a connection while an algorithm runs is how a
 * pool of ten serves four concurrent users — the shape of F-41, which cost this project eight hung
 * threads for twenty-five minutes.
 */
@Service
@RequiresDatabase
public class ItineraryPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryPersistenceService.class);

    private final ItineraryRepositoryPort itineraries;

    public ItineraryPersistenceService(ItineraryRepositoryPort itineraries) {
        this.itineraries = itineraries;
    }

    /**
     * Persists a scheduled plan, or refuses an infeasible one.
     *
     * <p>The {@code INFEASIBLE} check is not defensive duplication of
     * {@link Itinerary}'s own rule — it is the earlier, cheaper half. The aggregate refuses to
     * <em>be</em> unpublishable; this refuses to <em>store</em> a result that never produced an
     * aggregate at all, and says so with the day that failed rather than with a null.
     *
     * @return the saved plan, absent when the result was infeasible
     */
    @TransactionalWrite
    public Optional<Itinerary> save(SchedulingResult result) {
        if (result.outcome() == DayPlan.Outcome.INFEASIBLE) {
            log.info("itinerary_not_saved reason=infeasible notices={}",
                    result.allNotices().size());
            return Optional.empty();
        }
        Itinerary itinerary = result.itineraryIfBuilt().orElseThrow(() -> new IllegalStateException(
                "a non-INFEASIBLE SchedulingResult must carry an itinerary"));
        Itinerary saved = itineraries.save(itinerary);
        log.info("itinerary_saved trip={} days={} outcome={} unknown_data={}",
                saved.tripId(), saved.days().size(), result.outcome(), result.hasUnknownData());
        return Optional.of(saved);
    }

    /**
     * The trip's plan, scoped to its owner.
     *
     * <p>{@code readOnly} rather than {@link TransactionalWrite}: this is the timeline read, it runs
     * on every view of the itinerary page, and a read-only transaction lets Hibernate skip dirty
     * checking on an aggregate that eagerly loads its days and items.
     */
    @Transactional(readOnly = true)
    public Optional<Itinerary> findForTrip(UUID tripId, UUID userId) {
        return itineraries.findByTripIdAndUserId(tripId, userId);
    }

    /** Whether the trip has a plan at all, without assembling it. */
    @Transactional(readOnly = true)
    public boolean exists(UUID tripId) {
        return itineraries.existsByTripId(tripId);
    }
}
