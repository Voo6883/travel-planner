package com.travelplanner.ai.agent;

import com.travelplanner.ai.guardrails.ItineraryOutputGuardrails;
import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.port.ItineraryAgentPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic itinerary proposal for CI and for a checkout with no provider key (task 30).
 *
 * <p>The counterpart to {@code StubTravelResearchAgent}, and wired the same way: it is the default
 * bean, a live agent would be a second one behind a stronger {@code @ConditionalOnProperty}, and
 * PLAN's no-keys-in-CI rule is why the stub is not a placeholder but the thing the tests actually
 * exercise. See **F-52** for the same situation on C2.
 *
 * <h2>Its selection rule, stated plainly</h2>
 *
 * <p>Cluster by curated area, then take places in a stable order, one area per day. That is a
 * defensible plan — it is exactly UC-C3-07's "minimise cross-city transit" — and it is emphatically
 * not what a language model would produce, which is the point: the stub proves the <em>pipeline</em>
 * (propose → guardrails → schedule → route → persist), and the live agent later improves only the
 * proposal step. Nothing downstream has to change when it arrives.
 *
 * <p>No LLM call, so nothing here can time out, hallucinate, or cost anything. The same inputs
 * always produce the same proposal.
 */
public final class StubItineraryAgent implements ItineraryAgentPort {

    /** Recorded on every plan, so one built by this agent is identifiable in the ledger. */
    public static final String PROMPT_TEMPLATE_ID = "itinerary-stub";
    public static final int PROMPT_VERSION = 1;
    public static final String MODEL_NAME = "stub";

    /** Enough for a day the scheduler can fill without immediately hitting the pace ceiling. */
    private static final int STOPS_PER_DAY = 4;

    /**
     * <p>The guardrail pass at the end is not ceremony for a stub that cannot fabricate an id: it
     * is where the port's grounding contract is discharged, so a live agent added later inherits a
     * pipeline that already expects output to be checked here rather than by its caller.
     */
    @Override
    public ItineraryProposal propose(ItineraryGenerationRequest request) {
        List<ItineraryProposal.ProposedDay> days = new ArrayList<>(request.dayCount());
        Map<UUID, List<Poi>> byArea = clusterByArea(request.candidatePois());
        List<UUID> areaOrder = new ArrayList<>(byArea.keySet());

        for (int dayIndex = 0; dayIndex < request.dayCount(); dayIndex++) {
            // Areas cycle when there are fewer of them than days: a four-day trip to a city with
            // two curated areas revisits them rather than leaving two days empty, and the
            // trip-wide duplicate check still stops the same POI appearing twice.
            UUID areaId = areaOrder.isEmpty() ? null : areaOrder.get(dayIndex % areaOrder.size());
            List<Poi> pool = areaId == null ? List.of() : byArea.get(areaId);
            days.add(new ItineraryProposal.ProposedDay(
                    dayIndex + 1,
                    areaId,
                    takeStops(pool),
                    areaId == null ? null : "A day around one area"));
        }
        ItineraryProposal proposal = new ItineraryProposal(days, narrative(request),
                PROMPT_TEMPLATE_ID, PROMPT_VERSION, MODEL_NAME);
        ItineraryOutputGuardrails.validate(proposal, request);
        return proposal;
    }

    /**
     * Groups by curated area, dropping POIs nobody placed.
     *
     * <p>An unplaced POI is not scheduled by this agent at all. It could be — the scheduler would
     * accept it — but a day clustered around an area cannot honestly include a place that is not
     * known to be in it, and task 29 would resolve every leg to it as {@code UNKNOWN}.
     * {@code LinkedHashMap} keyed in POI order keeps the day-to-area assignment stable.
     */
    private static Map<UUID, List<Poi>> clusterByArea(List<Poi> pois) {
        Map<UUID, List<Poi>> byArea = new LinkedHashMap<>();
        pois.stream()
                .sorted(Comparator.comparing(Poi::slug))
                .forEach(poi -> poi.areaIdIfKnown().ifPresent(areaId ->
                        byArea.computeIfAbsent(areaId, key -> new ArrayList<>()).add(poi)));
        return byArea;
    }

    /**
     * Takes this day's stops, consuming them so the trip-wide duplicate guardrail is satisfied by
     * construction rather than by luck.
     */
    private static List<ItineraryProposal.ProposedStop> takeStops(List<Poi> pool) {
        List<ItineraryProposal.ProposedStop> stops = new ArrayList<>(STOPS_PER_DAY);
        for (int i = 0; i < STOPS_PER_DAY && !pool.isEmpty(); i++) {
            Poi poi = pool.remove(0);
            stops.add(new ItineraryProposal.ProposedStop(poi.id(), null));
        }
        return stops;
    }

    /**
     * Deliberately plain, and deliberately not a claim.
     *
     * <p>Every fact a traveller could act on comes from the scheduler, the route policy and the
     * knowledge base. A stub writing colourful prose would be inventing exactly the kind of
     * confident detail PLAN §4.1.0 exists to prevent.
     */
    private static String narrative(ItineraryGenerationRequest request) {
        return "A " + request.dayCount() + "-day plan for " + request.destinationName()
                + ", grouped by area to keep travel between stops short.";
    }
}
