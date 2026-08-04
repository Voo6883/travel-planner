package com.travelplanner.application.mobility;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.algorithm.mobility.LegRequest;
import com.travelplanner.domain.algorithm.mobility.RouteChoicePolicy;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryDay;
import com.travelplanner.domain.model.ItineraryItem;
import com.travelplanner.domain.model.ItineraryLeg;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.domain.port.ItineraryLegRepositoryPort;
import com.travelplanner.domain.port.KnowledgePort;
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
 * Resolves an itinerary's gaps into legs, and answers UC-C3-12's {@code get_route} (task 29).
 *
 * <h2>Load once, decide many times</h2>
 *
 * <p>The knowledge base is read <strong>once per plan</strong> — segments, modes, apps and
 * replacements are per-destination facts, and a plan of five days with six stops each would
 * otherwise issue a hundred queries to answer twenty-five gaps. Everything after that load is
 * {@link RouteChoicePolicy}, which is pure.
 *
 * <p>That ordering also keeps the write short: resolution happens before the transaction opens, for
 * the reason F-41 taught this project the hard way.
 *
 * <h2>What it will not do</h2>
 *
 * <p>No live provider, no LLM, no distance heuristic. The brief says never fabricate precision, and
 * the fallback ladder ends in {@code UNKNOWN} rather than in an estimate this class invented.
 */
@Service
@RequiresDatabase
public class RouteResolutionService {

    private static final Logger log = LoggerFactory.getLogger(RouteResolutionService.class);

    private final KnowledgePort knowledge;
    private final ItineraryLegRepositoryPort legs;
    private final RouteChoicePolicy policy;

    public RouteResolutionService(KnowledgePort knowledge, ItineraryLegRepositoryPort legs) {
        this.knowledge = knowledge;
        this.legs = legs;
        this.policy = new RouteChoicePolicy();
    }

    /**
     * Resolves and stores every gap in a plan (UC-C3-09).
     *
     * <p>A plan of n items per day produces n−1 legs per day — the chain item[k] → item[k+1], which
     * is why V27's {@code ordinal} is gapless and in clock order. A day with one item produces none,
     * and that is a complete answer rather than a failure.
     *
     * @return every leg written, in day order
     */
    @TransactionalWrite
    public List<ItineraryLeg> resolveForItinerary(Itinerary itinerary) {
        MobilityCorpus corpus = loadCorpus(itinerary.destinationId());
        List<ItineraryLeg> all = new ArrayList<>();

        for (ItineraryDay day : itinerary.days()) {
            List<ItineraryLeg> dayLegs = resolveDay(day, corpus);
            all.addAll(legs.replaceForDay(day.id(), dayLegs));
        }
        long unknown = all.stream().filter(ItineraryLeg::needsUserAttention).count();
        log.info("itinerary_legs_resolved trip={} legs={} unknown={}",
                itinerary.tripId(), all.size(), unknown);
        return List.copyOf(all);
    }

    /** The legs already stored for a plan, for a timeline read. */
    @Transactional(readOnly = true)
    public List<ItineraryLeg> findForItinerary(Itinerary itinerary) {
        return legs.findByDayIds(itinerary.days().stream().map(ItineraryDay::id).toList());
    }

    /**
     * UC-C3-12 — "how do I get from Gion to Arashiyama?"
     *
     * <p>Answered through the same ladder the itinerary was built on, so chat and timeline cannot
     * disagree. Empty means the corpus could not answer, which the caller must render as "I do not
     * know" rather than as silence.
     */
    @Transactional(readOnly = true)
    public Optional<ItineraryLeg> describeRoute(UUID destinationId, UUID fromPoiId, UUID toPoiId) {
        if (fromPoiId.equals(toPoiId)) {
            return Optional.empty();
        }
        MobilityCorpus corpus = loadCorpus(destinationId);
        return policy.describe(new LegRequest(fromPoiId, toPoiId,
                corpus.areaOf(fromPoiId), corpus.areaOf(toPoiId), corpus.segments(), corpus.modes(),
                corpus.apps(), corpus.replacements()));
    }

    private List<ItineraryLeg> resolveDay(ItineraryDay day, MobilityCorpus corpus) {
        List<ItineraryItem> items = day.items();
        List<ItineraryLeg> dayLegs = new ArrayList<>(Math.max(0, items.size() - 1));
        for (int i = 1; i < items.size(); i++) {
            ItineraryItem from = items.get(i - 1);
            ItineraryItem to = items.get(i);
            dayLegs.add(policy.resolve(new LegRequest(from.id(), to.id(),
                    corpus.areaOf(from.poiId()), corpus.areaOf(to.poiId()),
                    corpus.segments(), corpus.modes(), corpus.apps(), corpus.replacements())));
        }
        return dayLegs;
    }

    /** One read of everything the policy could need for this destination. */
    private MobilityCorpus loadCorpus(UUID destinationId) {
        Destination destination = knowledge.findDestinationById(destinationId).orElse(null);
        String countryCode = destination == null ? null : destination.countryCode();

        Map<UUID, UUID> poiAreas = new HashMap<>();
        for (Poi poi : knowledge.findPois(destinationId, Optional.empty())) {
            poi.areaIdIfKnown().ifPresent(areaId -> poiAreas.put(poi.id(), areaId));
        }
        return new MobilityCorpus(
                knowledge.findRouteSegments(destinationId),
                knowledge.findTransportModes(destinationId),
                countryCode == null ? List.of() : knowledge.findTravelApps(countryCode),
                countryCode == null ? List.of() : knowledge.findTravelAppReplacements(countryCode),
                poiAreas);
    }

    /**
     * Everything the policy needs for one destination, read once.
     *
     * @param poiAreas POI to its curated area. A POI absent from this map was never placed, which is
     *        what produces an {@code UNKNOWN} leg rather than a guess
     */
    private record MobilityCorpus(
            List<RouteSegment> segments,
            List<TransportMode> modes,
            List<TravelApp> apps,
            List<TravelAppReplacement> replacements,
            Map<UUID, UUID> poiAreas) {

        /** Null for a free-time block, an unfilled meal slot, or a POI nobody located. */
        UUID areaOf(UUID poiId) {
            return poiId == null ? null : poiAreas.get(poiId);
        }
    }
}
