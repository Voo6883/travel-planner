package com.travelplanner.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.ItineraryGenerationRequest;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The deterministic proposer that CI actually exercises.
 *
 * <p>It is not a placeholder: it is what proves the pipeline — propose, ground, schedule, route,
 * persist — end to end with no provider key, which is PLAN's rule for the suite. A live agent
 * replaces only the first step.
 */
class StubItineraryAgentTest {

    private static final UUID DESTINATION = UUID.randomUUID();
    private static final UUID SHIBUYA = UUID.randomUUID();
    private static final UUID ASAKUSA = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 4, 1);

    @Test
    void proposesOneDayPerCalendarDay() {
        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois(SHIBUYA, 6), START, START.plusDays(2)));

        assertThat(proposal.days()).extracting(ItineraryProposal.ProposedDay::dayNumber)
                .containsExactly(1, 2, 3);
    }

    /** UC-C3-07: a day is built around one area, which is what minimises cross-city transit. */
    @Test
    void clustersEachDayAroundASingleCuratedArea() {
        List<Poi> pois = new ArrayList<>(pois(SHIBUYA, 4));
        pois.addAll(pois(ASAKUSA, 4));

        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois, START, START.plusDays(1)));

        assertThat(proposal.days()).allSatisfy(day ->
                assertThat(day.areaId()).isNotNull());
        assertThat(proposal.days().get(0).areaId())
                .isNotEqualTo(proposal.days().get(1).areaId());
    }

    /**
     * The trip-wide duplicate rule is satisfied by construction — stops are consumed as they are
     * taken — rather than by hoping the guardrail never fires.
     */
    @Test
    void neverProposesTheSamePoiTwiceAcrossTheTrip() {
        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois(SHIBUYA, 12), START, START.plusDays(3)));

        List<UUID> referenced = proposal.referencedPoiIds();
        assertThat(referenced).doesNotHaveDuplicates();
    }

    /** Fewer areas than days: they cycle rather than leaving days empty. */
    @Test
    void reusesAnAreaWhenThereAreFewerAreasThanDays() {
        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois(SHIBUYA, 12), START, START.plusDays(2)));

        assertThat(proposal.days()).hasSize(3);
        assertThat(proposal.days()).allSatisfy(day ->
                assertThat(day.areaId()).isEqualTo(SHIBUYA));
    }

    /**
     * A POI nobody placed in an area is not scheduled. The scheduler would take it, but a day
     * clustered around an area cannot honestly include a place not known to be in it — and task 29
     * would resolve every leg to it as UNKNOWN.
     */
    @Test
    void leavesOutAPoiThatWasNeverPlacedInAnArea() {
        Poi unplaced = new Poi(UUID.randomUUID(), DESTINATION, null, "floating", "Floating", null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, provenance(), 0);
        List<Poi> pois = new ArrayList<>(pois(SHIBUYA, 2));
        pois.add(unplaced);

        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois, START, START));

        assertThat(proposal.referencedPoiIds()).doesNotContain(unplaced.id());
    }

    /** A destination with no placed POIs proposes empty days; the scheduler reports it. */
    @Test
    void proposesEmptyDaysWhenNothingIsPlacedRatherThanInventingStops() {
        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(List.of(), START, START));

        assertThat(proposal.days()).hasSize(1);
        assertThat(proposal.days().get(0).stops()).isEmpty();
        assertThat(proposal.days().get(0).areaId()).isNull();
    }

    @Test
    void stampsThePromptAndModelMetadataItWasBuiltWith() {
        ItineraryProposal proposal = new StubItineraryAgent().propose(
                request(pois(SHIBUYA, 2), START, START));

        assertThat(proposal.promptTemplateId()).isEqualTo(StubItineraryAgent.PROMPT_TEMPLATE_ID);
        assertThat(proposal.promptVersion()).isEqualTo(StubItineraryAgent.PROMPT_VERSION);
        assertThat(proposal.modelName()).isEqualTo(StubItineraryAgent.MODEL_NAME);
    }

    /** Same inputs, same proposal — the property that makes the pipeline testable at all. */
    @Test
    void producesTheSameProposalForTheSameInputs() {
        List<Poi> pois = pois(SHIBUYA, 8);

        ItineraryProposal first = new StubItineraryAgent().propose(
                request(pois, START, START.plusDays(1)));
        ItineraryProposal second = new StubItineraryAgent().propose(
                request(pois, START, START.plusDays(1)));

        assertThat(first.referencedPoiIds()).isEqualTo(second.referencedPoiIds());
        assertThat(first.narrative()).isEqualTo(second.narrative());
    }

    // ------------------------------------------------------------------------------------ setup

    private static ItineraryGenerationRequest request(
            List<Poi> candidates, LocalDate start, LocalDate end) {
        return new ItineraryGenerationRequest(UUID.randomUUID(), DESTINATION, "Tokyo", start, end,
                new TripBriefDetails(List.of("tokyo-jp"), false, null, null, null, null, null,
                        List.of(), TravelPace.MODERATE),
                candidates,
                List.of(area(SHIBUYA, "shibuya"), area(ASAKUSA, "asakusa")));
    }

    private static DestinationArea area(UUID id, String slug) {
        return new DestinationArea(id, DESTINATION, slug, slug, null, null, null, provenance());
    }

    private static List<Poi> pois(UUID areaId, int count) {
        List<Poi> pois = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String slug = areaId.equals(SHIBUYA) ? "s-" + i : "a-" + i;
            pois.add(new Poi(UUID.randomUUID(), DESTINATION, areaId, slug, slug, null,
                    PoiCategory.SIGHT, List.of(), "en", null, null, null, null, provenance(), 0));
        }
        return pois;
    }

    private static KnowledgeProvenance provenance() {
        return new KnowledgeProvenance("wikivoyage:tokyo", "Wikivoyage",
                KnowledgeLicence.CC_BY_SA_4_0, "© Wikivoyage contributors, CC BY-SA 4.0", null,
                TrustTier.COMMUNITY, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
