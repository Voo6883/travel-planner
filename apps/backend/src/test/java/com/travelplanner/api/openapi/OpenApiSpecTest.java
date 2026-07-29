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
        // Named explicitly rather than iterating every shared response: task 08 added a shared
        // *success* response (AuthSession), and a blanket "all responses are ApiErrorResponse"
        // assertion would have to be weakened every time one of those appears. This list is the
        // thing worth pinning — an error response that quietly grew its own body shape is how a
        // frontend ends up with two error parsers.
        Map<String, ApiResponse> responses = spec.getComponents().getResponses();
        List<String> errorResponses = List.of(
                "ValidationFailed", "Unauthorized", "Forbidden", "NotFound", "VersionConflict",
                "InternalError", "InvalidCredentials", "SignInForbidden", "AccountLocked",
                "InvalidProviderToken", "ProviderSignInForbidden", "ProviderLinkConflict",
                "ProviderEmailUnavailable", "ProviderUnavailable", "UserNotFound", "AccountClosed");

        assertThat(responses).containsKeys(errorResponses.toArray(String[]::new));
        errorResponses.forEach(name -> {
            ApiResponse response = responses.get(name);
            assertThat(response.getContent()).containsKey("application/json");
            Schema<?> schema = response.getContent().get("application/json").getSchema();
            assertThat(schemaNameOf(schema))
                    .describedAs("response %s", name)
                    .isEqualTo("ApiErrorResponse");
        });
    }

    @Test
    void theAuthSurfaceMatchesTheEndpointTableAdr009Publishes() {
        // PLAN §4.0.5's table, the two paths ADR 009 §5 records as missing from it, and the four
        // task 10 adds for the external providers that table already names.
        assertThat(spec.getPaths()).containsKeys(
                "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                "/auth/logout-all", "/auth/me",
                "/auth/firebase", "/auth/oauth/github/start", "/auth/oauth/github/callback",
                "/auth/providers/{provider}");
        assertThat(spec.getComponents().getSecuritySchemes()).containsKey("cookieAuth");
    }

    @Test
    void linkingAndUnlinkingRequireASession() {
        // ADR 009 §4: the explicit confirmation is only proof if somebody is signed in to give it.
        PathItem providers = spec.getPaths().get("/auth/providers/{provider}");
        assertThat(providers.getPost().getSecurity()).isNotEmpty();
        assertThat(providers.getDelete().getSecurity()).isNotEmpty();
    }

    @Test
    void noAuthResponseBodyCarriesAToken() {
        // ADR 002 rejected the Authorization header because a token JavaScript can read is a token
        // XSS can steal. Putting one in a JSON body would give that back. The two flags task 10
        // added are booleans about what just happened, not credentials.
        assertThat(spec.getComponents().getSchemas().get("AuthSessionResponse").getProperties())
                .containsOnlyKeys("user", "is_new_user", "provider_linked");
        assertThat(spec.getComponents().getSchemas().get("CurrentUser").getProperties())
                .doesNotContainKeys("access_token", "refresh_token", "token");
        assertThat(spec.getComponents().getSchemas().get("FirebaseAuthRequest").getProperties())
                .containsOnlyKeys("id_token");
    }

    @Test
    void theAdminSurfaceIsExactlyTheCapabilityTablePlanSection406Publishes() {
        assertThat(spec.getPaths()).containsKeys(
                "/admin/users", "/admin/users/{userId}", "/admin/users/{userId}/reset-password");

        PathItem detail = spec.getPaths().get("/admin/users/{userId}");
        assertThat(detail.getGet().getOperationId()).isEqualTo("getAdminUser");
        assertThat(detail.getPut().getOperationId()).isEqualTo("updateAdminUser");
        // No DELETE. PLAN §4.0.6 gives an administrator four capabilities and deleting somebody
        // else's account is not among them — UC-A14 is the owner's own action.
        assertThat(detail.getDelete()).isNull();
    }

    @Test
    void everyAdminOperationRequiresASession() {
        // The role check is Spring Security's; what the contract has to say is that none of these
        // is reachable anonymously. An admin path published without `security` would be an
        // open account-administration API the moment somebody generated a client from it.
        adminOperations().forEach(operation ->
                assertThat(operation.getSecurity())
                        .describedAs("security for %s", operation.getOperationId())
                        .isNotEmpty());
    }

    @Test
    void everyAdminMutationDeclaresTheForbiddenAndUserNotFoundResponses() {
        // PLAN §4.0.6 "standard errors: forbidden, user_not_found". A mutation that documents
        // neither leaves a client with no branch for the two outcomes it will actually meet.
        adminOperations().stream()
                .filter(operation -> !"listAdminUsers".equals(operation.getOperationId()))
                .forEach(operation -> assertThat(operation.getResponses())
                        .describedAs("responses for %s", operation.getOperationId())
                        .containsKeys("403", "404"));
    }

    @Test
    void noAdminSchemaPublishesACredentialOrAnotherUsersContent() {
        // The two invariants the whole admin slice rests on: an administrator manages accounts, and
        // never sees a credential. Both are properties of the published shape, so both are testable
        // here rather than being a convention somebody has to keep.
        List<String> exposed = List.of("AdminUserSummary", "AdminUserDetail");
        exposed.forEach(name -> assertThat(propertyNamesOf(name))
                .describedAs("properties of %s", name)
                .doesNotContain("password", "password_hash", "new_password", "token_version")
                .doesNotContain("trips", "trip_id", "conversations", "bookings"));

        // `has_local_password` is the single bit that survives: whether a reset applies at all.
        assertThat(propertyNamesOf("AdminUserDetail")).contains("has_local_password");
    }

    @Test
    void theCoverageEndpointIsPublicAndPublishesSlugsRatherThanIds() {
        // ADR 010 §4. `security` is deliberately absent: this backs the destination picker and the
        // agent's honest "I do not cover that yet", both of which run before anybody signs in. A
        // generated client that demanded a session here could not call it from the landing page.
        Operation supported = spec.getPaths().get("/destinations/supported").getGet();
        assertThat(supported.getOperationId()).isEqualTo("listSupportedDestinations");
        assertThat(supported.getSecurity()).isNull();

        // One public handle, not two. `destination_not_covered` already lists slugs in
        // `details.supported`, and a second identifier would split clients between them.
        assertThat(propertyNamesOf("SupportedDestination"))
                .contains("slug", "name", "country_code", "timezone")
                .doesNotContain("id", "destination_id", "coverage_level");
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
    void streamingOperationsAreMarkedAndTheirSuccessBodyIsOnlyEventStream() {
        // The marker is what tells the codegen-drift gate never to demand a generated client for
        // an SSE path (ADR 007). Task 20 filled it in with the two chat POSTs.
        //
        // Narrowed from "every response" to "the 2xx response" when those paths landed. The
        // original was written while nothing was marked, and it asserted something an SSE
        // operation cannot satisfy: a refusal that happens *before* the stream opens is an
        // ordinary `application/json` §6.1 envelope, and must be, because a client cannot read a
        // status with no body. Only once the response has committed to 200 does the envelope have
        // to travel in-band as `event: error` — which is a property of the 200 body, and is what
        // this now checks.
        allOperations().stream()
                .filter(operation -> Boolean.TRUE.equals(extension(operation, "x-sse-stream")))
                .forEach(operation -> operation.getResponses().entrySet().stream()
                        .filter(response -> response.getKey().startsWith("2"))
                        .forEach(response -> assertThat(response.getValue().getContent().keySet())
                                .containsExactly("text/event-stream")));
    }

    @Test
    void bothChatSendOperationsAreMarkedAsStreams() {
        // A path that streams without the marker is one the drift gate would try to generate a
        // client for, and ADR 007 records why that cannot work.
        assertThat(spec.getPaths().get("/planner/chat/messages").getPost())
                .satisfies(operation -> assertThat(extension(operation, "x-sse-stream")).isEqualTo(true));
        assertThat(spec.getPaths().get("/trips/{tripId}/chat/messages").getPost())
                .satisfies(operation -> assertThat(extension(operation, "x-sse-stream")).isEqualTo(true));
    }

    private static List<Operation> adminOperations() {
        return spec.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/admin/"))
                .flatMap(entry -> entry.getValue().readOperations().stream())
                .toList();
    }

    /**
     * Property names of a component schema, flattened through {@code allOf}. Both admin response
     * schemas compose {@code PageMetadata} or {@code AdminUserSummary}, and a check that read only
     * the outer object's own properties would pass while the composed half published anything at
     * all.
     */
    private static List<String> propertyNamesOf(String schemaName) {
        return flattenedProperties(spec.getComponents().getSchemas().get(schemaName));
    }

    private static List<String> flattenedProperties(Schema<?> schema) {
        if (schema == null) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>();
        if (schema.getProperties() != null) {
            names.addAll(schema.getProperties().keySet());
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(part -> names.addAll(flattenedProperties(part)));
        }
        return names;
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
