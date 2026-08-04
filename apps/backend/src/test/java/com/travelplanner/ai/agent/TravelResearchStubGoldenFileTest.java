package com.travelplanner.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.tool.KnowledgeResearchTools;
import com.travelplanner.ai.tool.ResearchToolJson;
import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.algorithm.ranking.DestinationRanker;
import com.travelplanner.domain.algorithm.ranking.RankingInput;
import com.travelplanner.domain.algorithm.ranking.ScoringWeights;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelResearchRequest;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden-file pin for the deterministic stub research agent (AI-AGENT-WORKFLOW A3).
 *
 * <p>Editing the stub's narrative shape without updating the fixture fails CI rather than silently
 * changing what RESEARCH_READY clients see.
 */
class TravelResearchStubGoldenFileTest {

    @Test
    void stubNarrativeMatchesGoldenFixture() throws Exception {
        JsonNode golden = readGolden();
        StubTravelResearchAgentTest.FakeKnowledge knowledge =
                new StubTravelResearchAgentTest.FakeKnowledge();
        Destination tokyo = knowledge.seedFull("tokyo-jp", "JP");
        ResearchToolJson json = new ResearchToolJson(new ObjectMapper());
        StubTravelResearchAgent agent = new StubTravelResearchAgent(
                new KnowledgeResearchTools(knowledge, json), json, 40, 24_000);

        TripBriefDetails brief = TripBriefDetails.empty()
                .withDates(new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8)))
                .withDateFlexibility(DateFlexibility.FIXED)
                .withDepartureCity("Singapore")
                .withBudget(Money.of("3000", "USD"))
                .withParty(PartySize.ofAdults(2))
                .withInterests(List.of(TravelInterest.FOOD, TravelInterest.SIGHTSEEING))
                .withPace(TravelPace.MODERATE);
        DestinationCandidate candidate = StubTravelResearchAgentTest.fullCandidate(tokyo);
        var ranking = new DestinationRanker().topK(new RankingInput(
                List.of(candidate), brief, ScoringWeights.defaults(), 5,
                Instant.parse("2026-06-01T00:00:00Z")));

        TravelResearchNarratives narratives = agent.research(
                new TravelResearchRequest(brief, List.of(candidate), ranking, pct -> { }));

        assertThat(narratives.promptTemplateId()).isEqualTo(golden.get("prompt_template_id").asText());
        assertThat(narratives.promptVersion()).isEqualTo(golden.get("prompt_version").asInt());
        assertThat(narratives.modelName()).isEqualTo(golden.get("model_name").asText());
        DestinationNarrative narrative = narratives.narratives().getFirst();
        assertThat(narrative.travelerGuide().overview())
                .contains(golden.get("overview_contains").asText());
        assertThat(narrative.sourceRefs().getFirst().sourceRef())
                .isEqualTo(golden.get("source_ref").asText());
        assertThat(narrative.rationale()).contains(golden.get("rationale_contains").asText());
    }

    private static JsonNode readGolden() throws Exception {
        try (InputStream in = TravelResearchStubGoldenFileTest.class.getResourceAsStream(
                "/ai/travel-research-stub-golden.json")) {
            assertThat(in).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }
}
