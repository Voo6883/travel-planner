package com.travelplanner.ai.guardrails;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rejects a proposal that asserts anything the knowledge base does not hold (task 30: "every POI
 * must resolve to a known ID/source", "do not invent POIs").
 *
 * <h2>Why the whole proposal is rejected, never the offending stop</h2>
 *
 * <p>Dropping a fabricated stop and keeping the rest looks like the forgiving choice and is the
 * dangerous one: the narrative the agent wrote still describes the temple it invented, so the plan
 * now contradicts its own prose, and nobody is told. Rejecting outright hands the caller a typed
 * failure and one bounded repair attempt — which is the behaviour the brief asks for.
 *
 * <p>These run <strong>before</strong> the scheduler. Validating feasibility for a plan built on a
 * place that does not exist would waste the work and, worse, could produce a schedule that looks
 * authoritative.
 */
public final class ItineraryOutputGuardrails {

    private ItineraryOutputGuardrails() {
    }

    /**
     * @throws ValidationFailedException naming the first thing that could not be grounded
     */
    public static void validate(ItineraryProposal proposal, ItineraryGenerationRequest request) {
        Set<UUID> knownPois = request.candidatePois().stream()
                .map(Poi::id)
                .collect(Collectors.toSet());
        Set<UUID> knownAreas = request.areas().stream()
                .map(DestinationArea::id)
                .collect(Collectors.toSet());

        rejectFabricatedPois(proposal, knownPois);
        rejectFabricatedAreas(proposal, knownAreas);
        rejectDuplicateStops(proposal);
        rejectWrongDayCount(proposal, request);
    }

    /** The grounding contract. An id the request did not offer was invented. */
    private static void rejectFabricatedPois(ItineraryProposal proposal, Set<UUID> knownPois) {
        for (UUID poiId : proposal.referencedPoiIds()) {
            if (!knownPois.contains(poiId)) {
                throw ValidationFailedException.field("days[].stops[].poi_id",
                        "names a POI that is not in the candidate set: " + poiId);
            }
        }
    }

    /**
     * UC-C3-07's clustering may only name a curated area.
     *
     * <p>A day clustered around an area nobody curated cannot be rendered, and — more quietly — the
     * scheduler would carry the id straight into {@code itinerary_day.area_id}, where V27's foreign
     * key would reject the whole write with a constraint error naming no cause a reader could act
     * on.
     */
    private static void rejectFabricatedAreas(ItineraryProposal proposal, Set<UUID> knownAreas) {
        for (ItineraryProposal.ProposedDay day : proposal.days()) {
            UUID areaId = day.areaId();
            if (areaId != null && !knownAreas.contains(areaId)) {
                throw ValidationFailedException.field("days[].area_id",
                        "names an area that is not curated for this destination: " + areaId);
            }
        }
    }

    /**
     * The same place twice across the whole trip.
     *
     * <p>Checked here rather than left to the scheduler, which deduplicates within one day but has
     * no reason to know that Tuesday already visited what Thursday proposes. A traveller sent to
     * the same temple twice in one week reads it as a bug, and it is.
     */
    private static void rejectDuplicateStops(ItineraryProposal proposal) {
        Set<UUID> seen = new HashSet<>();
        for (UUID poiId : proposal.referencedPoiIds()) {
            if (!seen.add(poiId)) {
                throw ValidationFailedException.field("days[].stops[].poi_id",
                        "proposes the same POI more than once across the trip: " + poiId);
            }
        }
    }

    /**
     * One proposed day per calendar day.
     *
     * <p>A short proposal is the common malformed shape — a model asked for five days returns
     * three and narrates as though it gave five. The scheduler would refuse it later anyway, but
     * the message here names the real problem instead of reporting an unfillable day.
     */
    private static void rejectWrongDayCount(
            ItineraryProposal proposal, ItineraryGenerationRequest request) {
        int expected = request.dayCount();
        List<ItineraryProposal.ProposedDay> days = proposal.days();
        if (days.size() != expected) {
            throw ValidationFailedException.field("days", "the trip runs " + expected
                    + (expected == 1 ? " day" : " days") + " but the proposal has " + days.size());
        }
    }
}
