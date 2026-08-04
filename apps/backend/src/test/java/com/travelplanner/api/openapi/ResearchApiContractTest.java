package com.travelplanner.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The C2 research surface as published (UC-C2-01/02/03/06, PLAN §3.1 / §4.1).
 */
class ResearchApiContractTest {

    private static final String RUN = "/trips/{tripId}/research/run";
    private static final String JOB = "/trips/{tripId}/research/jobs/{jobId}";
    private static final String RANKED = "/trips/{tripId}/ranked-recommendations";
    private static final String SELECT = "/trips/{tripId}/selected-recommendation";
    private static final String GUIDE = "/destinations/{destinationId}/guide";

    private static OpenAPI spec;

    @BeforeAll
    static void loadSpec() {
        spec = OpenApiSpec.load(OpenApiSpec.CONTRACT);
    }

    @Test
    void theResearchSurfaceIncludesJobsListSelectAndGuide() {
        assertThat(spec.getPaths()).containsKeys(RUN, JOB, RANKED, SELECT, GUIDE);
        assertThat(spec.getPaths().get(RUN).getPost().getOperationId()).isEqualTo("startResearch");
        assertThat(spec.getPaths().get(JOB).getGet().getOperationId()).isEqualTo("getResearchJob");
        assertThat(spec.getPaths().get(RANKED).getGet().getOperationId())
                .isEqualTo("listRankedRecommendations");
        assertThat(spec.getPaths().get(SELECT).getPost().getOperationId())
                .isEqualTo("selectRecommendation");
        assertThat(spec.getPaths().get(GUIDE).getGet().getOperationId())
                .isEqualTo("getDestinationGuide");
    }

    @Test
    void startingAResearchRunIsAcceptedNotCompleted() {
        PathItem run = spec.getPaths().get(RUN);
        assertThat(run.getPost().getResponses()).containsKey("202");
        assertThat(run.getPost().getResponses()).doesNotContainKey("200");
    }

    @Test
    void rankedListReturnsResearchNotReadyWhenIncomplete() {
        assertThat(spec.getPaths().get(RANKED).getGet().getResponses()).containsKey("409");
    }

    @Test
    void bothResearchJobOperationsRequireASession() {
        assertThat(spec.getPaths().get(RUN).getPost().getSecurity()).isNotEmpty();
        assertThat(spec.getPaths().get(JOB).getGet().getSecurity()).isNotEmpty();
        assertThat(spec.getPaths().get(RANKED).getGet().getSecurity()).isNotEmpty();
        assertThat(spec.getPaths().get(SELECT).getPost().getSecurity()).isNotEmpty();
    }

    @Test
    void theStatusVocabularyIsTheLowerCaseWireForm() {
        Schema<?> status = spec.getComponents().getSchemas().get("ResearchJobStatus");
        assertThat(status.getEnum()).map(Object::toString)
                .containsExactly("queued", "running", "completed", "failed");
    }

    @Test
    void theJobPayloadCarriesItsKeyFieldsAndNoOwner() {
        Schema<?> job = spec.getComponents().getSchemas().get("ResearchJob");
        assertThat(job.getRequired())
                .contains("job_id", "trip_id", "status", "progress_pct", "attempts");
        assertThat(job.getProperties())
                .describedAs("a job must not publish its owner; ownership is proven on the trip")
                .doesNotContainKey("user_id");
    }

    @Test
    void recommendationPayloadCarriesGuideCostAndSources() {
        Schema<?> rec = spec.getComponents().getSchemas().get("RankedRecommendation");
        assertThat(rec.getRequired()).contains(
                "recommendation_id", "rationale", "traveler_guide", "source_refs",
                "score_breakdown");
        Schema<?> list = spec.getComponents().getSchemas().get("RankedRecommendations");
        assertThat(list.getRequired()).contains("no_confident_result", "recommendations");
    }
}
