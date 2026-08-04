package com.travelplanner.domain.algorithm.mobility;

import com.travelplanner.domain.enums.LegResolution;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.model.ItineraryLeg;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves one A→B gap into a leg, or refuses honestly (UC-C3-09/10, UC-K10; task 29 DoD).
 *
 * <h2>The resolution ladder</h2>
 *
 * <p>Four rungs, tried in order, each a weaker claim than the one above it. The order is the whole
 * algorithm, and it is fixed rather than scored because "which of these is the better answer" has an
 * obvious ranking — a curated fact beats an estimate, and an estimate beats a guess — while a scoring
 * function would let a well-tuned weight put a guess above a fact.
 *
 * <ol>
 *   <li><strong>{@code CURATED_SEGMENT}</strong> — a segment between these two areas that the corpus
 *       did not mark {@code estimated}. Cited by id.</li>
 *   <li><strong>{@code SAME_AREA_WALK}</strong> — both stops in one curated neighbourhood. Areas are
 *       curated as walkable, so this is a claim about topology rather than a guess about distance.
 *       Written below the first rung for readability only: {@link RouteSegment} refuses a segment
 *       from an area to itself and {@code SampleSeedDomainCheck} rejects a seed that tries (F-44),
 *       so the two can never compete for one pair. Pinned by
 *       {@code cannotHoldACuratedSegmentWithinASingleArea}.</li>
 *   <li><strong>{@code AREA_ESTIMATE}</strong> — a segment exists but the corpus flagged it
 *       {@code estimated}. Carried through as an estimate rather than flattened into a fact;
 *       {@code RouteSegment.estimated()} exists precisely so the two stay distinguishable.</li>
 *   <li><strong>{@code UNKNOWN}</strong> — nothing above applied. Claims nothing at all.</li>
 * </ol>
 *
 * <p><strong>There is no fifth rung that invents a number.</strong> No distance heuristic, no
 * average city speed, no "about 20 minutes". The brief says never fabricate precision, and the way
 * to hold that line is to have nowhere in this class that could.
 *
 * <p>Segments are looked up in both directions: a corpus that curates Shibuya→Asakusa has described
 * the return trip too, and requiring both rows would double the curation burden to state a symmetric
 * fact. Where both exist the forward one wins, so the choice stays deterministic.
 */
public final class RouteChoicePolicy {

    /** Bumped when resolution changes in a way that would produce a different leg. */
    public static final String ALGORITHM_VERSION = "route-choice-policy-v1";

    private final Supplier<UUID> ids;

    public RouteChoicePolicy(Supplier<UUID> ids) {
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public RouteChoicePolicy() {
        this(UUID::randomUUID);
    }

    /** Resolves the gap. Never throws for an unanswerable one — that is {@code UNKNOWN}. */
    public ItineraryLeg resolve(LegRequest request) {
        Objects.requireNonNull(request, "request");

        RouteSegment curated = bestSegment(request, false);
        if (curated != null) {
            return legFrom(request, curated, LegResolution.CURATED_SEGMENT);
        }
        if (request.isWithinOneArea()) {
            return sameAreaWalk(request);
        }
        RouteSegment estimated = bestSegment(request, true);
        if (estimated != null) {
            return legFrom(request, estimated, LegResolution.AREA_ESTIMATE);
        }
        return unknown(request);
    }

    /**
     * The best segment joining the two areas, in the requested confidence class.
     *
     * <p>Ties broken by duration then by id: two curated routes between the same pair are
     * alternatives, the shorter is the better default, and the id keeps the answer stable when even
     * that matches.
     */
    private static RouteSegment bestSegment(LegRequest request, boolean estimated) {
        if (!request.bothEndsLocated()) {
            return null;
        }
        return request.segments().stream()
                .filter(segment -> segment.estimated() == estimated)
                .filter(segment -> joins(segment, request.fromAreaId(), request.toAreaId()))
                .min(Comparator.comparingLong(RouteSegment::durationMinutes)
                        .thenComparing(RouteSegment::id))
                .orElse(null);
    }

    /** Either direction — a symmetric fact should not need two curated rows to be usable. */
    private static boolean joins(RouteSegment segment, UUID fromAreaId, UUID toAreaId) {
        return (segment.fromAreaId().equals(fromAreaId) && segment.toAreaId().equals(toAreaId))
                || (segment.fromAreaId().equals(toAreaId) && segment.toAreaId().equals(fromAreaId));
    }

    private ItineraryLeg legFrom(
            LegRequest request, RouteSegment segment, LegResolution resolution) {
        TransportMode mode = request.modes().stream()
                .filter(candidate -> candidate.id().equals(segment.transportModeId()))
                .findFirst()
                .orElse(null);
        // A segment whose mode was not curated alongside it cannot state UC-C3-10's mode, and a leg
        // without a mode is not a resolved leg. Refusing beats defaulting to WALK, which would put a
        // traveller on foot across a city.
        if (mode == null) {
            return unknown(request);
        }
        List<TravelApp> apps = TravelAppSelector.forMode(
                mode.kind(), request.apps(), request.replacements());
        return new ItineraryLeg(
                ids.get(),
                request.fromItemId(),
                request.toItemId(),
                resolution,
                mode.kind(),
                (int) segment.durationMinutes(),
                mode.costBand(),
                // Only a curated segment may cite one — the record enforces this too.
                resolution.isCurated() ? segment.id() : null,
                segment.provenance().sourceRef(),
                segment.notes(),
                apps.stream().map(TravelApp::id).toList());
    }

    /**
     * Both stops in one curated neighbourhood.
     *
     * <p>The duration is {@link ItineraryLeg#SAME_AREA_WALK_MINUTES}, a declared constant. Deriving
     * it from coordinates would imply a surveyed route; declaring it says plainly that this is the
     * standing assumption about what "one area" means.
     */
    private ItineraryLeg sameAreaWalk(LegRequest request) {
        List<TravelApp> apps = TravelAppSelector.forMode(
                TransportKind.WALK, request.apps(), request.replacements());
        return new ItineraryLeg(ids.get(), request.fromItemId(), request.toItemId(),
                LegResolution.SAME_AREA_WALK, TransportKind.WALK,
                ItineraryLeg.SAME_AREA_WALK_MINUTES, null, null, null,
                "Both stops are in the same area — a short walk.",
                apps.stream().map(TravelApp::id).toList());
    }

    /** Shown, and claiming nothing. See {@link LegResolution#UNKNOWN}. */
    private ItineraryLeg unknown(LegRequest request) {
        return new ItineraryLeg(ids.get(), request.fromItemId(), request.toItemId(),
                LegResolution.UNKNOWN, null, null, null, null, null,
                "No curated route between these stops — check locally before you travel.",
                List.of());
    }

    /**
     * UC-C3-12's read, as a value rather than a leg: "how do I get from Gion to Arashiyama?"
     *
     * <p>Same ladder, so the answer a traveller reads in chat is the answer their itinerary was built
     * on. Two code paths would eventually disagree, and the one that disagreed would be the one
     * nobody had tested.
     */
    public Optional<ItineraryLeg> describe(LegRequest request) {
        ItineraryLeg leg = resolve(request);
        return leg.needsUserAttention() ? Optional.empty() : Optional.of(leg);
    }
}
