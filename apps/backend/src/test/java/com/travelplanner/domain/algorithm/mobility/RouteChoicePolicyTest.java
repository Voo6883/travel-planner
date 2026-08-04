package com.travelplanner.domain.algorithm.mobility;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.AppReplacementReason;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.LegResolution;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.model.ItineraryLeg;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The resolution ladder and the suppression rule (task 29: "tests for walking, metro/train,
 * ride-hail, ferry, missing segment, conflicting/stale data, local-app replacement, and source
 * attribution").
 */
class RouteChoicePolicyTest {

    private static final UUID DESTINATION = UUID.randomUUID();
    private static final UUID SHIBUYA = UUID.randomUUID();
    private static final UUID ASAKUSA = UUID.randomUUID();
    private static final UUID FROM_ITEM = UUID.randomUUID();
    private static final UUID TO_ITEM = UUID.randomUUID();
    private static final Instant RETRIEVED = Instant.parse("2026-01-01T00:00:00Z");

    // ------------------------------------------------------------------------ the ladder, in order

    @Test
    void prefersACuratedSegmentAndCitesIt() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment segment = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(segment),
                List.of(metro), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.CURATED_SEGMENT);
        assertThat(leg.mode()).isEqualTo(TransportKind.METRO);
        assertThat(leg.durationMinutes()).isEqualTo(35);
        assertThat(leg.costBand()).isEqualTo(PriceBand.BUDGET);
        assertThat(leg.routeSegmentId()).isEqualTo(segment.id());
        assertThat(leg.sourceRef()).isEqualTo("wikivoyage:tokyo");
        assertThat(leg.isCurated()).isTrue();
    }

    /**
     * The knowledge base cannot hold a segment from an area to itself — {@code RouteSegment} refuses
     * one, and {@code SampleSeedDomainCheck} rejects a seed file that tries (F-44).
     *
     * <p>So the first rung and the second can never compete for the same pair, and the ordering
     * between them is unobservable rather than merely untested. Asserted here because the policy's
     * ladder reads as though they could, and a reader deserves to know which rule makes that moot.
     */
    @Test
    void cannotHoldACuratedSegmentWithinASingleArea() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> segment(SHIBUYA, SHIBUYA, metro, 8, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void treatsTwoStopsInOneAreaAsAShortWalk() {
        ItineraryLeg leg = policy().resolve(request(SHIBUYA, SHIBUYA, List.of(), List.of(),
                List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.SAME_AREA_WALK);
        assertThat(leg.mode()).isEqualTo(TransportKind.WALK);
        assertThat(leg.durationMinutes()).isEqualTo(ItineraryLeg.SAME_AREA_WALK_MINUTES);
        // A topology claim, not a survey — so no segment is cited and no cost is asserted.
        assertThat(leg.routeSegmentId()).isNull();
        assertThat(leg.costBand()).isNull();
    }

    /** The corpus's own honesty flag is carried through rather than flattened into a fact. */
    @Test
    void carriesAnEstimatedSegmentThroughAsAnEstimate() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment estimated = segment(SHIBUYA, ASAKUSA, metro, 40, true);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(estimated),
                List.of(metro), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.AREA_ESTIMATE);
        assertThat(leg.durationMinutes()).isEqualTo(40);
        assertThat(leg.isCurated()).isFalse();
        // An estimate may not wear a citation it did not earn.
        assertThat(leg.routeSegmentId()).isNull();
    }

    @Test
    void prefersTheCuratedSegmentOverAnEstimatedOneForTheSamePair() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment estimated = segment(SHIBUYA, ASAKUSA, metro, 20, true);
        RouteSegment curated = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA,
                List.of(estimated, curated), List.of(metro), List.of(), List.of()));

        // The curated one wins even though it is slower. A shorter estimate is still a guess.
        assertThat(leg.resolution()).isEqualTo(LegResolution.CURATED_SEGMENT);
        assertThat(leg.durationMinutes()).isEqualTo(35);
    }

    // ---------------------------------------------------------------------- honest refusal

    @Test
    void refusesToInventALegItCannotAnswer() {
        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(), List.of(),
                List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.UNKNOWN);
        assertThat(leg.needsUserAttention()).isTrue();
        assertThat(leg.durationMinutes()).isNull();
        assertThat(leg.mode()).isNull();
        assertThat(leg.costBand()).isNull();
        assertThat(leg.routeSegmentId()).isNull();
        assertThat(leg.recommendedAppIds()).isEmpty();
        assertThat(leg.instructions()).contains("check locally");
    }

    /** The corpus cannot route from a place it never located. */
    @Test
    void refusesWhenAnEndpointWasNeverPlacedInAnArea() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment segment = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(null, ASAKUSA, List.of(segment),
                List.of(metro), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.UNKNOWN);
    }

    /**
     * Conflicting data: a segment whose mode was never curated alongside it. Defaulting to WALK
     * would put a traveller on foot across a city, so the leg is refused instead.
     */
    @Test
    void refusesASegmentWhoseTransportModeIsMissingFromTheCorpus() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment orphaned = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(orphaned),
                List.of(), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.UNKNOWN);
    }

    /** UC-C3-12: chat gets the same ladder, so it cannot disagree with the itinerary. */
    @Test
    void describesNothingRatherThanGuessingForAChatQuery() {
        assertThat(policy().describe(request(SHIBUYA, ASAKUSA, List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
    }

    // -------------------------------------------------------------------------------- symmetry

    @Test
    void answersTheReturnTripFromTheSameCuratedSegment() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment outbound = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(ASAKUSA, SHIBUYA, List.of(outbound),
                List.of(metro), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.CURATED_SEGMENT);
        assertThat(leg.durationMinutes()).isEqualTo(35);
    }

    // ------------------------------------------------------------------------------ every mode

    /** Walking, metro, train, ride-hail, ferry and the rest all resolve and keep their kind. */
    @ParameterizedTest
    @EnumSource(TransportKind.class)
    void resolvesEveryCuratedTransportKind(TransportKind kind) {
        TransportMode transportMode = mode(kind, PriceBand.MODERATE);
        RouteSegment segment = segment(SHIBUYA, ASAKUSA, transportMode, 25, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(segment),
                List.of(transportMode), List.of(), List.of()));

        assertThat(leg.resolution()).isEqualTo(LegResolution.CURATED_SEGMENT);
        assertThat(leg.mode()).isEqualTo(kind);
    }

    // ------------------------------------------------------------------------- app suppression

    /**
     * UC-K14, and the reason this rule exists at all: Uber in Shanghai is not a worse suggestion
     * than 滴滴, it is an app the traveller cannot use while standing on a kerb.
     */
    @Test
    void neverRecommendsAGlobalAppTheCorpusSaysIsReplacedHere() {
        TransportMode taxi = mode(TransportKind.RIDESHARE, PriceBand.MODERATE);
        TravelApp didi = app("didi", TravelAppCategory.RIDEHAILING);
        TravelApp uber = app("uber", TravelAppCategory.RIDEHAILING);
        RouteSegment segment = segment(SHIBUYA, ASAKUSA, taxi, 20, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(segment),
                List.of(taxi), List.of(didi, uber), List.of(replacement(didi, "uber"))));

        assertThat(leg.recommendedAppIds()).containsExactly(didi.id()).doesNotContain(uber.id());
    }

    /** A metro leg wants the transit app and the card that pays for it — the traveller's real need. */
    @Test
    void recommendsTransitAndPaymentForAPublicTransportLeg() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        TravelApp transit = app("tokyo-metro", TravelAppCategory.TRANSIT);
        TravelApp suica = app("suica", TravelAppCategory.PAYMENT);
        TravelApp maps = app("maps", TravelAppCategory.NAVIGATION);
        RouteSegment segment = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(segment),
                List.of(metro), List.of(transit, suica, maps), List.of()));

        assertThat(leg.recommendedAppIds()).containsExactly(transit.id(), suica.id());
    }

    /** A walk needs navigation, not a payment card. */
    @Test
    void recommendsOnlyNavigationForAWalk() {
        TravelApp maps = app("maps", TravelAppCategory.NAVIGATION);
        TravelApp suica = app("suica", TravelAppCategory.PAYMENT);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, SHIBUYA, List.of(), List.of(),
                List.of(maps, suica), List.of()));

        assertThat(leg.recommendedAppIds()).containsExactly(maps.id());
    }

    @Test
    void recommendsNothingForALegItCouldNotDescribe() {
        TravelApp maps = app("maps", TravelAppCategory.NAVIGATION);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(), List.of(),
                List.of(maps), List.of()));

        assertThat(leg.recommendedAppIds()).isEmpty();
    }

    // ----------------------------------------------------------------------------- determinism

    @Test
    void producesTheSameLegForTheSameInputs() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment a = segment(SHIBUYA, ASAKUSA, metro, 35, false);
        RouteSegment b = segment(SHIBUYA, ASAKUSA, metro, 35, false);

        ItineraryLeg first = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(a, b),
                List.of(metro), List.of(), List.of()));
        ItineraryLeg second = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(b, a),
                List.of(metro), List.of(), List.of()));

        // Equal durations, so the id tiebreak decides — and decides the same way both times.
        assertThat(first.routeSegmentId()).isEqualTo(second.routeSegmentId());
    }

    @Test
    void prefersTheShorterOfTwoCuratedAlternatives() {
        TransportMode metro = mode(TransportKind.METRO, PriceBand.BUDGET);
        RouteSegment slow = segment(SHIBUYA, ASAKUSA, metro, 50, false);
        RouteSegment quick = segment(SHIBUYA, ASAKUSA, metro, 20, false);

        ItineraryLeg leg = policy().resolve(request(SHIBUYA, ASAKUSA, List.of(slow, quick),
                List.of(metro), List.of(), List.of()));

        assertThat(leg.durationMinutes()).isEqualTo(20);
    }

    // ------------------------------------------------------------------------------------ setup

    private static RouteChoicePolicy policy() {
        AtomicLong counter = new AtomicLong();
        return new RouteChoicePolicy(() -> new UUID(0L, counter.incrementAndGet()));
    }

    private static LegRequest request(UUID fromArea, UUID toArea, List<RouteSegment> segments,
            List<TransportMode> modes, List<TravelApp> apps, List<TravelAppReplacement> swaps) {
        return new LegRequest(FROM_ITEM, TO_ITEM, fromArea, toArea, segments, modes, apps, swaps);
    }

    private static KnowledgeProvenance provenance() {
        return new KnowledgeProvenance("wikivoyage:tokyo", "Wikivoyage",
                KnowledgeLicence.CC_BY_SA_4_0, "© Wikivoyage contributors, CC BY-SA 4.0",
                "https://wikivoyage.org/Tokyo", TrustTier.COMMUNITY, RETRIEVED);
    }

    private static RouteSegment segment(
            UUID from, UUID to, TransportMode mode, int minutes, boolean estimated) {
        return new RouteSegment(UUID.randomUUID(), DESTINATION, from, to, mode.id(),
                Duration.ofMinutes(minutes), estimated, "Take the line and change once.",
                provenance());
    }

    private static TransportMode mode(TransportKind kind, PriceBand band) {
        return new TransportMode(UUID.randomUUID(), DESTINATION,
                kind.name().toLowerCase(java.util.Locale.ROOT), kind.name(), kind, null, band,
                true, provenance());
    }

    private static TravelApp app(String slug, TravelAppCategory category) {
        return new TravelApp(UUID.randomUUID(), "JP", slug, slug, category, null,
                "https://apps.apple.com/app/" + slug, null, provenance());
    }

    private static TravelAppReplacement replacement(TravelApp local, String replacedSlug) {
        return new TravelAppReplacement(UUID.randomUUID(), local.id(), replacedSlug, replacedSlug,
                AppReplacementReason.NOT_AVAILABLE, "Not operating in this market.", provenance());
    }
}
