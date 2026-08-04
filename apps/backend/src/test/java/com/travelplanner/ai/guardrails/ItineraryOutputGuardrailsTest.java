package com.travelplanner.ai.guardrails;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The grounding contract (task 30: "every POI must resolve to a known ID/source", plus the
 * golden cases for duplicate POIs and fabricated IDs).
 *
 * <p>Each of these is a way a language model plausibly fails, and each rejects the <em>whole</em>
 * proposal rather than dropping the offending part — a plan silently missing what its own narrative
 * promised is worse than one that failed loudly.
 */
class ItineraryOutputGuardrailsTest {

    private static final UUID DESTINATION = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 4, 1);

    @Test
    void acceptsAProposalWhereEveryStopComesFromTheCandidateSet() {
        Poi tsukiji = poi("tsukiji");
        Poi senso = poi("senso-ji");

        assertThatCode(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, stops(tsukiji, senso)))),
                request(List.of(tsukiji, senso), START, START)))
                .doesNotThrowAnyException();
    }

    /** The headline failure mode: a place the knowledge base has never heard of. */
    @Test
    void rejectsAStopNamingAPoiThatWasNeverOffered() {
        Poi offered = poi("tsukiji");
        UUID invented = UUID.randomUUID();

        assertThatThrownBy(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, List.of(new ItineraryProposal.ProposedStop(invented, null))))),
                request(List.of(offered), START, START)))
                .isInstanceOf(ValidationFailedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ValidationFailedException.class))
                .extracting(failure -> failure.details().get("fields").toString())
                .asString()
                .contains("not in the candidate set");
    }

    /**
     * A day clustered around an area nobody curated would carry the id into
     * {@code itinerary_day.area_id}, where V27's foreign key rejects the whole write with a message
     * naming no cause a reader can act on. Caught here instead.
     */
    @Test
    void rejectsADayClusteredAroundAnAreaThatIsNotCurated() {
        Poi offered = poi("tsukiji");

        assertThatThrownBy(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(new ItineraryProposal.ProposedDay(
                        1, UUID.randomUUID(), stops(offered), null))),
                request(List.of(offered), START, START)))
                .isInstanceOf(ValidationFailedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ValidationFailedException.class))
                .extracting(failure -> failure.details().get("fields").toString())
                .asString()
                .contains("not curated for this destination");
    }

    /** A day with no area is legitimate — a travel day, or an uncurated destination. */
    @Test
    void acceptsADayWithNoAreaAtAll() {
        Poi offered = poi("tsukiji");

        assertThatCode(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(new ItineraryProposal.ProposedDay(1, null, stops(offered), null))),
                request(List.of(offered), START, START)))
                .doesNotThrowAnyException();
    }

    /**
     * The scheduler deduplicates within a day but has no reason to know Tuesday already visited
     * what Thursday proposes. A traveller sent to the same temple twice reads it as a bug.
     */
    @Test
    void rejectsTheSamePoiProposedOnTwoDifferentDays() {
        Poi repeated = poi("senso-ji");
        Poi other = poi("tsukiji");

        assertThatThrownBy(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, stops(repeated)), day(2, stops(other, repeated)))),
                request(List.of(repeated, other), START, START.plusDays(1))))
                .isInstanceOf(ValidationFailedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ValidationFailedException.class))
                .extracting(failure -> failure.details().get("fields").toString())
                .asString()
                .contains("more than once across the trip");
    }

    /** The common malformed shape: asked for five days, returns three, narrates as though five. */
    @Test
    void rejectsAProposalWithFewerDaysThanTheTrip() {
        Poi offered = poi("tsukiji");

        assertThatThrownBy(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, stops(offered)))),
                request(List.of(offered), START, START.plusDays(2))))
                .isInstanceOf(ValidationFailedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ValidationFailedException.class))
                .extracting(failure -> failure.details().get("fields").toString())
                .asString()
                .contains("runs 3 days but the proposal has 1");
    }

    @Test
    void rejectsAProposalWithMoreDaysThanTheTrip() {
        Poi a = poi("a");
        Poi b = poi("b");

        assertThatThrownBy(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, stops(a)), day(2, stops(b)))),
                request(List.of(a, b), START, START)))
                .isInstanceOf(ValidationFailedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        ValidationFailedException.class))
                .extracting(failure -> failure.details().get("fields").toString())
                .asString()
                .contains("runs 1 day but the proposal has 2");
    }

    /** An empty day is the scheduler's problem to report, not a grounding failure. */
    @Test
    void leavesAnEmptyDayToTheSchedulerRatherThanRejectingItHere() {
        Poi offered = poi("tsukiji");

        assertThatCode(() -> ItineraryOutputGuardrails.validate(
                proposal(List.of(day(1, stops(offered)), day(2, List.of()))),
                request(List.of(offered), START, START.plusDays(1))))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------ setup

    private static ItineraryProposal proposal(List<ItineraryProposal.ProposedDay> days) {
        return new ItineraryProposal(days, "A plan.", "itinerary-stub", 1, "stub");
    }

    private static ItineraryProposal.ProposedDay day(
            int dayNumber, List<ItineraryProposal.ProposedStop> stops) {
        return new ItineraryProposal.ProposedDay(dayNumber, AREA, stops, null);
    }

    private static List<ItineraryProposal.ProposedStop> stops(Poi... pois) {
        return java.util.Arrays.stream(pois)
                .map(poi -> new ItineraryProposal.ProposedStop(poi.id(), null))
                .toList();
    }

    private static ItineraryGenerationRequest request(
            List<Poi> candidates, LocalDate start, LocalDate end) {
        return new ItineraryGenerationRequest(UUID.randomUUID(), DESTINATION, "Tokyo", start, end,
                new TripBriefDetails(List.of("tokyo-jp"), false, null, null, null, null, null,
                        List.of(), TravelPace.MODERATE),
                candidates,
                List.of(new DestinationArea(AREA, DESTINATION, "shibuya", "Shibuya", null, null,
                        null, provenance())));
    }

    private static Poi poi(String slug) {
        return new Poi(UUID.randomUUID(), DESTINATION, AREA, slug, slug, null, PoiCategory.SIGHT,
                List.of(), "en", null, null, null, null, provenance(), 0);
    }

    private static KnowledgeProvenance provenance() {
        return new KnowledgeProvenance("wikivoyage:tokyo", "Wikivoyage",
                KnowledgeLicence.CC_BY_SA_4_0, "© Wikivoyage contributors, CC BY-SA 4.0", null,
                TrustTier.COMMUNITY, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
