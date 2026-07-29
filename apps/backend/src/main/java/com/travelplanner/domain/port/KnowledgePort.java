package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to the Travel Knowledge Base (PLAN §4.1.0, ADR 010).
 *
 * <p><strong>Read-only, and user-independent.</strong> There is no {@code save} here and no
 * {@code userId} on any method. The TKB describes the world rather than anybody's trip, so it is
 * the one repository port in this system that is not scoped by the caller's identity — PLAN
 * §4.0.2-L's "every query filters by user_id" is about user-owned data, and none of this is. Task
 * 41 owns curation writes; task 40 owns re-embedding writes. Adding a mutator here would couple
 * retrieval to authoring and hand every reader a way to edit the corpus.
 *
 * <p><strong>Absence is typed, never empty-and-hope.</strong> A missing guide is
 * {@link Optional#empty()}; a destination that was never curated is
 * {@code DestinationNotCoveredException} raised by the caller through
 * {@link Destination#requireRankable(List)}. Those are different facts and the API says so: one is
 * "no narrative for this locale", the other is "we have never looked at this city". Collapsing
 * them into an empty list is what makes an uncovered destination indistinguishable from a poor
 * match, which ADR 010 §4 exists to prevent.
 *
 * <p>Every returned model carries its {@code KnowledgeProvenance}, so a caller that displays a fact
 * always holds the citation it is obliged to display and the age it must reason about. That is
 * PLAN §4.1.0's "provenance on every field" expressed as a return type rather than as a convention.
 */
public interface KnowledgePort {

    /** The destination with this slug, whatever its coverage. Absent when the slug is unknown. */
    Optional<Destination> findDestinationBySlug(String slug);

    Optional<Destination> findDestinationById(UUID destinationId);

    /**
     * Every destination eligible for C2 ranking, in a stable order.
     *
     * <p>This backs both {@code GET /api/v1/destinations/supported} and the agent's honest "I do
     * not cover that yet" reply, which is why it returns the whole list rather than a page: the
     * refusal has to name the alternatives, and three slugs do not need paging.
     */
    List<Destination> findSupportedDestinations();

    /** The narrative for a destination in a locale. Absent when that locale was not curated. */
    Optional<DestinationGuide> findGuide(UUID destinationId, String locale);

    List<DestinationArea> findAreas(UUID destinationId);

    /** Every POI for a destination, or only one category when {@code category} is present. */
    List<Poi> findPois(UUID destinationId, Optional<PoiCategory> category);

    List<TransportMode> findTransportModes(UUID destinationId);

    /**
     * Curated legs between areas.
     *
     * <p>Only curated pairs are stored (ADR 010 Consequences), so a pair with no row is a real
     * absence rather than a gap to be filled by the model. The caller derives an estimate with
     * {@code RouteSegment.estimated() == true}, and the flag is what keeps the two distinguishable.
     */
    List<RouteSegment> findRouteSegments(UUID destinationId);

    /** App packs are per country, not per city — Grab covers Thailand, not just Bangkok. */
    List<TravelApp> findTravelApps(String countryCode);

    /** All twelve months for a FULL destination, ordered January to December. */
    List<SeasonalityMonth> findSeasonality(UUID destinationId);

    List<PriceObservation> findPriceHistory(UUID destinationId, String category);

    /**
     * Hybrid semantic search within one destination (ADR 010 §5).
     *
     * <p>Results are ordered most-similar first and already filtered by the query's similarity
     * floor, so an empty list means "nothing was similar enough" rather than "nothing exists". The
     * distinction matters to the agent: the first is a reason to widen the question, the second is
     * a reason to stop.
     */
    List<KnowledgeMatch> search(KnowledgeQuery query);
}
