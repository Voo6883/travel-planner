package com.travelplanner.application.itinerary;

import com.travelplanner.application.mobility.RouteResolutionService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.algorithm.scheduling.DayPlan;
import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest;
import com.travelplanner.domain.algorithm.scheduling.ItineraryScheduler;
import com.travelplanner.domain.algorithm.scheduling.SchedulingCandidate;
import com.travelplanner.domain.algorithm.scheduling.SchedulingResult;
import com.travelplanner.domain.enums.ItineraryItemCategory;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryLeg;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.ItineraryAgentPort;
import com.travelplanner.domain.port.KnowledgePort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C3 generation: propose, ground, schedule, route, persist (task 30, UC-C3-01).
 *
 * <h2>The order is the design</h2>
 *
 * <pre>
 *   agent proposes  →  guardrails ground it  →  scheduler decides feasibility
 *                                            →  route policy fills the gaps
 *                                            →  one short transaction persists
 * </pre>
 *
 * <p><strong>The validators are authoritative and the agent cannot overrule them.</strong> That is
 * not a convention here — {@link ItineraryProposal} has nowhere to put a time, so a model cannot
 * express a schedule even if its prompt told it to, and {@code Itinerary} refuses to be
 * {@code READY} unless every day holds something. The agent picks places; arithmetic decides
 * whether they work.
 *
 * <h2>Nothing slow happens inside a transaction</h2>
 *
 * <p>The brief forbids calling the LLM or route tools inside one, and F-41 is the reason this
 * project takes that seriously. Everything above — including the repair loop — runs outside; the
 * write at the end is the only transactional step, and it is short.
 */
@Service
@RequiresDatabase
public class ItineraryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationService.class);

    /**
     * The original proposal plus one repair, matching {@code StructuredOutputRunner}'s budget.
     *
     * <p>Two is not timidity. A model that produced an ungroundable plan twice is not one attempt
     * away from success, and each extra round is a billed call and a slower answer for a traveller
     * already waiting.
     */
    static final int MAX_ATTEMPTS = 2;

    /** Enough candidates for a rich plan without handing the agent the whole corpus. */
    private static final int MAX_CANDIDATES = 60;

    private final KnowledgePort knowledge;
    private final ItineraryAgentPort agent;
    private final ItineraryPersistenceService itineraries;
    private final RouteResolutionService routes;
    private final ItineraryStatusWriter statusWriter;
    private final ItineraryScheduler scheduler = new ItineraryScheduler();

    public ItineraryGenerationService(
            KnowledgePort knowledge,
            ItineraryAgentPort agent,
            ItineraryPersistenceService itineraries,
            RouteResolutionService routes,
            ItineraryStatusWriter statusWriter) {
        this.knowledge = knowledge;
        this.agent = agent;
        this.itineraries = itineraries;
        this.routes = routes;
        this.statusWriter = statusWriter;
    }

    /**
     * Generates and persists a plan for a trip in {@code DESTINATION_SELECTED}.
     *
     * <p>Not {@code @Transactional}: see the class note. The one write is delegated to
     * {@link ItineraryStatusWriter}, which owns the boundary.
     *
     * @param destinationId the destination from the selected recommendation, resolved by the caller
     *        because that lookup is task 26's surface rather than this one's
     */
    public ItineraryGenerationOutcome generate(
            Trip trip, TripBrief brief, UUID destinationId, LocalDate startDate, LocalDate endDate) {
        if (trip.status() != TripStatus.DESTINATION_SELECTED) {
            return ItineraryGenerationOutcome.failed(
                    ItineraryGenerationOutcome.Failure.WRONG_TRIP_STATUS,
                    "expected DESTINATION_SELECTED, was " + trip.status(), 1);
        }
        if (destinationId == null) {
            return ItineraryGenerationOutcome.failed(
                    ItineraryGenerationOutcome.Failure.NO_SELECTED_DESTINATION,
                    "the trip has no selected destination", 1);
        }

        Destination destination = knowledge.findDestinationById(destinationId).orElse(null);
        List<Poi> candidates = loadCandidates(destinationId);
        if (destination == null || candidates.isEmpty()) {
            return ItineraryGenerationOutcome.failed(
                    ItineraryGenerationOutcome.Failure.INSUFFICIENT_KNOWLEDGE,
                    "no curated POIs for this destination", 1);
        }

        ItineraryGenerationRequest request = new ItineraryGenerationRequest(trip.id(),
                destinationId, destination.name(), startDate, endDate, brief.details(), candidates,
                knowledge.findAreas(destinationId));

        return attemptGeneration(trip, destination, request);
    }

    /**
     * The bounded loop.
     *
     * <p>Each attempt is proposal → grounding → scheduling. A failure at either stage is worth one
     * more try, because both are the model's fault and a second sample may differ; a failure at the
     * provider is not, because retrying a dead provider is just a slower error.
     */
    private ItineraryGenerationOutcome attemptGeneration(
            Trip trip, Destination destination, ItineraryGenerationRequest request) {
        ItineraryGenerationOutcome lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ItineraryProposal proposal;
            try {
                // Grounding is the adapter's own responsibility, asserted by the port contract:
                // `application/` may not import `ai/`, and ArchUnit enforces it. A guardrail call
                // here would also be the wrong shape — a second implementation could skip it.
                proposal = agent.propose(request);
            } catch (AiProviderException unavailable) {
                log.warn("itinerary_generation_provider_failed trip={} attempt={}",
                        trip.id(), attempt);
                return ItineraryGenerationOutcome.failed(
                        ItineraryGenerationOutcome.Failure.PROVIDER_UNAVAILABLE,
                        unavailable.getMessage(), attempt);
            } catch (ValidationFailedException ungrounded) {
                log.info("itinerary_generation_ungrounded trip={} attempt={} detail={}",
                        trip.id(), attempt, ungrounded.getMessage());
                lastFailure = ItineraryGenerationOutcome.failed(
                        ItineraryGenerationOutcome.Failure.UNGROUNDED_PROPOSAL,
                        ungrounded.getMessage(), attempt);
                continue;
            }

            SchedulingResult scheduled = scheduler.schedule(
                    toSchedulingRequest(trip, request, proposal, destination.timezone()));
            if (scheduled.outcome() == DayPlan.Outcome.INFEASIBLE) {
                log.info("itinerary_generation_infeasible trip={} attempt={}", trip.id(), attempt);
                lastFailure = ItineraryGenerationOutcome.failed(
                        ItineraryGenerationOutcome.Failure.NOT_SCHEDULABLE,
                        "the scheduler could not fill every day", attempt);
                continue;
            }
            return persist(trip, scheduled, attempt);
        }
        return lastFailure;
    }

    /**
     * One transaction, entered only once a plan has passed every validator.
     *
     * <p>Legs are resolved inside it as well: they are pure computation over an already-loaded
     * corpus plus one write, and splitting them out would let a plan exist for a moment with no
     * routes — a state the timeline would render as a day of stops with no way between them.
     */
    private ItineraryGenerationOutcome persist(Trip trip, SchedulingResult scheduled, int attempt) {
        Itinerary saved = itineraries.save(scheduled).orElse(null);
        if (saved == null) {
            return ItineraryGenerationOutcome.failed(
                    ItineraryGenerationOutcome.Failure.NOT_SCHEDULABLE,
                    "the plan was rejected at persistence", attempt);
        }
        List<ItineraryLeg> legs = routes.resolveForItinerary(saved);
        statusWriter.markItineraryReady(trip);
        log.info("itinerary_generated trip={} days={} legs={} attempts={}",
                trip.id(), saved.days().size(), legs.size(), attempt);
        return ItineraryGenerationOutcome.success(saved, legs, attempt);
    }

    /**
     * Turns the agent's selection into scheduler input.
     *
     * <p>The translation is where the division of labour becomes concrete: the proposal contributes
     * <em>which</em> POIs and their order (as {@code priority}), and every temporal fact —
     * durations, the day window, meal slots, pace — comes from the knowledge base or from policy
     * defaults. The agent's ordering is honoured as a preference, not as a schedule.
     */
    private ItineraryScheduler.TripPlanRequest toSchedulingRequest(
            Trip trip, ItineraryGenerationRequest request, ItineraryProposal proposal,
            String timezone) {
        Map<UUID, Poi> byId = new HashMap<>();
        request.candidatePois().forEach(poi -> byId.put(poi.id(), poi));

        List<DayPlanRequest> days = new ArrayList<>(proposal.days().size());
        for (ItineraryProposal.ProposedDay day : proposal.days()) {
            List<SchedulingCandidate> candidates = new ArrayList<>(day.stops().size());
            int priority = 0;
            for (ItineraryProposal.ProposedStop stop : day.stops()) {
                Poi poi = byId.get(stop.poiId());
                if (poi == null) {
                    continue;
                }
                candidates.add(toCandidate(poi, priority++));
            }
            days.add(new DayPlanRequest(day.dayNumber(),
                    request.startDate().plusDays(day.dayNumber() - 1L), day.areaId(),
                    DayPlanRequest.DayWindow.standard(), candidates,
                    DayPlanRequest.MealSlot.standard(), DayPlanRequest.PacePolicy.standard()));
        }
        return new ItineraryScheduler.TripPlanRequest(trip.id(), trip.userId(),
                request.destinationId(), request.startDate(), request.endDate(), timezone, days);
    }

    /**
     * A POI as the scheduler sees it.
     *
     * <p>Opening hours are passed through exactly as curated — absent stays absent, so the
     * scheduler reports {@code UNKNOWN_HOURS} rather than this method inventing a 09:00. The visit
     * duration is a policy default per category because the knowledge base does not curate one;
     * that is a stated assumption, not a measurement, and it lives in one place.
     */
    private static SchedulingCandidate toCandidate(Poi poi, int priority) {
        ItineraryItemCategory category = poi.isFood()
                ? ItineraryItemCategory.FOOD
                : ItineraryItemCategory.SIGHT;
        return new SchedulingCandidate(poi.id(), poi.name(), category,
                defaultVisitMinutes(category), null, null, poi.areaId(),
                poi.provenance().sourceRef(), 0, priority);
    }

    /** Stated assumptions, in one place, because the corpus curates no visit durations. */
    private static int defaultVisitMinutes(ItineraryItemCategory category) {
        return category == ItineraryItemCategory.FOOD ? 60 : 90;
    }

    /** The most-cited POIs first, bounded so a large corpus cannot blow the prompt. */
    private List<Poi> loadCandidates(UUID destinationId) {
        return knowledge.findPois(destinationId, Optional.empty()).stream()
                .limit(MAX_CANDIDATES)
                .toList();
    }

    /** Read of an existing plan, for callers that must not regenerate one. */
    @Transactional(readOnly = true)
    public Optional<Itinerary> find(UUID tripId, UUID userId) {
        return itineraries.findForTrip(tripId, userId);
    }
}
