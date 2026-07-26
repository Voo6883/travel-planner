package com.travelplanner.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The contract is the source of truth for two apps, so it is treated as production code: it must
 * parse, it must obey the locked HTTP conventions, and the shared primitives every later task
 * composes must keep the shape those tasks were designed against.
 */
class OpenApiSpecTest {

    private static OpenAPI spec;

    @BeforeAll
    static void loadSpec() {
        spec = OpenApiSpec.load(OpenApiSpec.CONTRACT);
    }

    @Test
    void errorCatalogDocumentAlsoValidates() {
        // Validated separately: an invalid errors.yaml can still resolve into openapi.yaml as an
        // opaque schema, so validating only the root document would not catch it.
        assertThat(OpenApiSpec.load(OpenApiSpec.ERROR_CATALOG)).isNotNull();
    }

    @Test
    void versionLivesInTheServerUrlSoPathKeysCarryItExactlyOnce() {
        assertThat(spec.getServers()).hasSize(1);
        assertThat(spec.getServers().get(0).getUrl()).isEqualTo("/api/v1");
        assertThat(spec.getPaths().keySet()).noneMatch(path -> path.startsWith("/api/"));
    }

    @Test
    void healthAndReadinessAreReflectedInTheSpec() {
        assertThat(spec.getPaths()).containsKeys("/health", "/ready");
        assertThat(spec.getPaths().get("/health").getGet().getOperationId()).isEqualTo("getHealth");

        PathItem ready = spec.getPaths().get("/ready");
        assertThat(ready.getGet().getOperationId()).isEqualTo("getReadiness");
        assertThat(ready.getGet().getResponses()).containsKeys("200", "503");
    }

    @Test
    void noOperationUsesPatch() {
        // PLAN §6.1 and ADR 008 §3: partial updates are typed POST actions, never PATCH.
        assertThat(spec.getPaths().values()).allSatisfy(path -> assertThat(path.getPatch()).isNull());
    }

    @Test
    void pathSegmentsAreKebabCasePlurals() {
        assertThat(spec.getPaths().keySet())
                .allMatch(path -> path.matches("^(/(\\{[a-zA-Z]+}|[a-z][a-z0-9-]*))+$"),
                        "kebab-case segments and {camelCase} path variables only");
    }

    @Test
    void everyResponseDocumentsTheRequestIdHeader() {
        // The header is how a user-reported failure is found in the logs; a response that omits it
        // is a response nobody can trace.
        allResponses().forEach(response ->
                assertThat(headerNamesOf(response)).contains("X-Request-Id"));
    }

    @Test
    void everySharedErrorResponseReturnsTheStandardEnvelope() {
        Map<String, ApiResponse> responses = spec.getComponents().getResponses();
        assertThat(responses).containsKeys(
                "ValidationFailed", "Unauthorized", "Forbidden", "NotFound", "VersionConflict",
                "InternalError");
        responses.values().forEach(response -> {
            assertThat(response.getContent()).containsKey("application/json");
            Schema<?> schema = response.getContent().get("application/json").getSchema();
            assertThat(schemaNameOf(schema)).isEqualTo("ApiErrorResponse");
        });
    }

    @Test
    void paginationParametersMatchThePublishedDefaultsAndCeiling() {
        Map<String, Parameter> parameters = spec.getComponents().getParameters();
        assertThat(parameters).containsKeys("PageParam", "PageSizeParam", "SortParam");

        Schema<?> pageSize = parameters.get("PageSizeParam").getSchema();
        assertThat(parameters.get("PageSizeParam").getName()).isEqualTo("page_size");
        assertThat(pageSize.getDefault()).isEqualTo(20);
        assertThat(pageSize.getMaximum().intValue()).isEqualTo(100);
        assertThat(parameters.get("SortParam").getSchema().getDefault()).isEqualTo("-created_at");
    }

    @Test
    void paginationMetadataCarriesPagePageSizeAndTotal() {
        Schema<?> metadata = spec.getComponents().getSchemas().get("PageMetadata");
        assertThat(metadata.getRequired()).containsExactlyInAnyOrder("page", "page_size", "total");
    }

    @Test
    void expectedVersionIsARequiredBodyFieldNotAHeader() {
        // ADR 008 §2. If this ever becomes optional, a missing version silently means
        // "force overwrite" — the exact data loss the ADR exists to prevent.
        Schema<?> expectedVersion = spec.getComponents().getSchemas().get("ExpectedVersion");
        assertThat(expectedVersion.getRequired()).containsExactly("expected_version");
        assertThat(expectedVersion.getProperties()).containsKey("expected_version");

        Schema<?> conflictDetails = spec.getComponents().getSchemas().get("VersionConflictDetails");
        assertThat(conflictDetails.getRequired()).containsExactly("current_version");
    }

    @Test
    void streamingOperationsAreMarkedAndCarryOnlyEventStreamContent() {
        // Inert until tasks 20/21 add the two chat paths. It exists now so the codegen-drift gate
        // can never start demanding a generated client for an SSE path (ADR 007).
        allOperations().stream()
                .filter(operation -> Boolean.TRUE.equals(extension(operation, "x-sse-stream")))
                .forEach(operation -> operation.getResponses().values().forEach(response ->
                        assertThat(response.getContent().keySet())
                                .containsExactly("text/event-stream")));
    }

    private static List<Operation> allOperations() {
        return spec.getPaths().values().stream().flatMap(path -> path.readOperations().stream()).toList();
    }

    private static List<ApiResponse> allResponses() {
        return allOperations().stream()
                .flatMap(operation -> operation.getResponses().values().stream())
                .toList();
    }

    private static Iterable<String> headerNamesOf(ApiResponse response) {
        return response.getHeaders() == null ? List.of() : response.getHeaders().keySet();
    }

    private static Object extension(Operation operation, String name) {
        return operation.getExtensions() == null ? null : operation.getExtensions().get(name);
    }

    /** Resolved schemas keep {@code $ref} when the target is a named component. */
    private static String schemaNameOf(Schema<?> schema) {
        String ref = schema.get$ref();
        return ref == null ? schema.getName() : ref.substring(ref.lastIndexOf('/') + 1);
    }
}
