package com.travelplanner.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The C2 research surface as published (UC-C2-01/02, PLAN §3.1). Separate from {@code OpenApiSpecTest}
 * so the properties specific to the durable-job slice — the {@code 202}, the lower-case status
 * vocabulary, and the absence of any owner field — are pinned where a later edit would weaken them.
 */
class ResearchApiContractTest {

    private static final String RUN = "/trips/{tripId}/research/run";
    private static final String JOB = "/trips/{tripId}/research/jobs/{jobId}";

    private static OpenAPI spec;

    @BeforeAll
    static void loadSpec() {
        spec = OpenApiSpec.load(OpenApiSpec.CONTRACT);
    }

    @Test
    void theResearchSurfaceIsExactlyTheTwoOperationsTaskTwentyThreeOwns() {
        assertThat(spec.getPaths()).containsKeys(RUN, JOB);
        assertThat(spec.getPaths().get(RUN).getPost().getOperationId()).isEqualTo("startResearch");
        assertThat(spec.getPaths().get(JOB).getGet().getOperationId()).isEqualTo("getResearchJob");
    }

    @Test
    void startingAResearchRunIsAcceptedNotCompleted() {
        // 202, not 200: the run is accepted onto a background worker, never finished on this call.
        PathItem run = spec.getPaths().get(RUN);
        assertThat(run.getPost().getResponses()).containsKey("202");
        assertThat(run.getPost().getResponses()).doesNotContainKey("200");
    }

    @Test
    void bothResearchOperationsRequireASession() {
        assertThat(spec.getPaths().get(RUN).getPost().getSecurity()).isNotEmpty();
        assertThat(spec.getPaths().get(JOB).getGet().getSecurity()).isNotEmpty();
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
}
