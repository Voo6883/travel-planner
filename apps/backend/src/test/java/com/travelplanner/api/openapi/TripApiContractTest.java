package com.travelplanner.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The C1 surface as published, held to the rules it is judged by (ADR 008, ADR 010 §4, PLAN §6.1).
 *
 * <p>Separate from {@code OpenApiSpecTest}, which pins the conventions every path shares. These are
 * the properties specific to the trip slice — and the ones a later edit is most likely to weaken
 * without noticing, because each of them is one line in a large document.
 */
class TripApiContractTest {

    private static OpenAPI spec;

    @BeforeAll
    static void loadSpec() {
        spec = OpenApiSpec.load(OpenApiSpec.CONTRACT);
    }

    @Test
    void theTripSurfaceIsExactlyTheOperationsTaskEighteenOwns() {
        assertThat(spec.getPaths()).containsKeys(
                "/trips",
                "/trips/{tripId}",
                "/trips/{tripId}/actions/archive",
                "/trips/{tripId}/brief",
                "/trips/{tripId}/brief/actions/answer-clarification");

        assertThat(spec.getPaths().get("/trips").getGet().getOperationId()).isEqualTo("listTrips");
        assertThat(spec.getPaths().get("/trips").getPost().getOperationId()).isEqualTo("createTrip");

        PathItem trip = spec.getPaths().get("/trips/{tripId}");
        assertThat(trip.getGet().getOperationId()).isEqualTo("getTrip");
        assertThat(trip.getPut().getOperationId()).isEqualTo("renameTrip");
        assertThat(trip.getDelete().getOperationId()).isEqualTo("deleteTrip");
    }

    @Test
    void everyTripOperationRequiresASession() {
        // There is no anonymous view of somebody's trips. An operation published without
        // `security` would be an open surface the moment a client was generated from it.
        tripOperations().forEach(operation -> assertThat(operation.getSecurity())
                .describedAs("security for %s", operation.getOperationId())
                .isNotEmpty());
    }

    @Test
    void answeringClarificationIsAPostActionAndNotAPutOrAPatch() {
        // ADR 008 §3 supersedes PLAN §4.1.3's `PUT .../brief/clarification`. The path must stay a
        // POST action: a PUT here would be the whole-aggregate re-send the ADR rejected.
        PathItem action = spec.getPaths().get("/trips/{tripId}/brief/actions/answer-clarification");

        assertThat(action.getPost().getOperationId()).isEqualTo("answerTripBriefClarification");
        assertThat(action.getPut()).isNull();
        assertThat(action.getPatch()).isNull();
        assertThat(spec.getPaths()).doesNotContainKey("/trips/{tripId}/brief/clarification");
    }

    @Test
    void everyTripMutationComposesExpectedVersionAndDeclaresTheConflictResponse() {
        // ADR 008 §2. A mutation that dropped either would be a lost update with no way for the
        // loser to find out, which is the whole defect the ADR exists to close.
        List<String> versioned = List.of(
                "RenameTripRequest", "ArchiveTripRequest",
                "UpdateTripBriefRequest", "AnswerClarificationRequest");

        versioned.forEach(name -> assertThat(propertyNamesOf(name))
                .describedAs("%s composes ExpectedVersion", name)
                .contains("expected_version"));

        List.of("renameTrip", "archiveTrip", "updateTripBrief", "answerTripBriefClarification")
                .forEach(operationId -> assertThat(operationOf(operationId).getResponses())
                        .describedAs("responses for %s", operationId)
                        .containsKey("409"));
    }

    @Test
    void deleteCarriesNoBodyAndThereforeNoVersion() {
        // A deliberate exception, documented on the operation: a delete is not based on prior
        // content, and a request body on DELETE is something intermediaries may drop.
        Operation delete = operationOf("deleteTrip");

        assertThat(delete.getRequestBody()).isNull();
        assertThat(delete.getResponses()).containsKeys("204", "404").doesNotContainKey("409");
    }

    @Test
    void creatingATripIsNotVersionedBecauseThereIsNoPriorStateToLose() {
        Schema<?> create = spec.getComponents().getSchemas().get("CreateTripRequest");

        assertThat(create.getProperties()).containsOnlyKeys("name");
        assertThat(operationOf("createTrip").getResponses())
                .containsKey("201")
                .doesNotContainKey("409");
    }

    @Test
    void noRequestBodyAnywhereAcceptsATripStatus() {
        // Status is derived from brief completeness. A client able to set it could declare
        // BRIEF_COMPLETE over an empty brief and step past the gate in front of C2.
        List.of("CreateTripRequest", "RenameTripRequest", "ArchiveTripRequest",
                        "UpdateTripBriefRequest", "AnswerClarificationRequest")
                .forEach(name -> assertThat(propertyNamesOf(name))
                        .describedAs("properties of %s", name)
                        .doesNotContain("status", "trip_status"));
    }

    @Test
    void noTripSchemaPublishesTheOwnerOrAnotherUsersData() {
        // Ownership comes from the session on every request, and no endpoint on this API accepts a
        // user id — publishing one would hand clients an identifier nothing takes back.
        assertThat(propertyNamesOf("Trip")).doesNotContain("user_id", "owner_id");
        assertThat(propertyNamesOf("TripBrief")).doesNotContain("user_id", "id", "brief_id");
    }

    @Test
    void moneyIsADecimalStringRatherThanAJsonNumber() {
        // PLAN §4.0.2-A end to end: a JSON number reaches a JavaScript client as a double and
        // undoes the BigDecimal/numeric chain at the last hop.
        Schema<?> money = spec.getComponents().getSchemas().get("Money");
        Schema<?> amount = (Schema<?>) money.getProperties().get("amount");

        assertThat(amount.getType()).isEqualTo("string");
        assertThat(amount.getPattern()).isNotBlank();
    }

    @Test
    void theBriefResponseAlwaysCarriesTheClarificationAndTheTripStatus() {
        // The three are only consistent together; a client that fetched them separately would
        // render a "start research" button for a brief the agent had just re-opened.
        Schema<?> brief = spec.getComponents().getSchemas().get("TripBrief");

        assertThat(brief.getRequired()).contains("clarification", "status", "version", "trip_id");
        assertThat(spec.getComponents().getSchemas().get("Clarification").getRequired())
                .containsExactly("questions");
        assertThat(propertyNamesOf("ClarificationQuestion"))
                .contains("id", "prompt_key", "type", "options", "required");
    }

    @Test
    void everyBriefMutationReturnsTheNewFullResource() {
        // ADR 008 §2. An acknowledgement would leave the client without the incremented version
        // its next write must echo, and without the clarification the save recomputed.
        List.of("getTripBrief", "updateTripBrief", "answerTripBriefClarification")
                .forEach(operationId -> assertThat(responseSchemaOf(operationId, "200"))
                        .describedAs("200 body of %s", operationId)
                        .isEqualTo("TripBrief"));

        List.of("renameTrip", "archiveTrip").forEach(operationId ->
                assertThat(responseSchemaOf(operationId, "200"))
                        .describedAs("200 body of %s", operationId)
                        .isEqualTo("Trip"));
    }

    @Test
    void savingABriefDeclaresTheUncoveredDestinationRefusal() {
        // ADR 010 §4: an uncovered destination is a typed 404 with the supported list, never a
        // silently accepted slug that later ranks near zero.
        assertThat(responseSchemaOf("updateTripBrief", "404")).isEqualTo("ApiErrorResponse");
        assertThat(spec.getComponents().getResponses().get("TripBriefNotFound").getDescription())
                .contains("destination_not_covered")
                .contains("details.supported");
    }

    private static List<Operation> tripOperations() {
        return spec.getPaths().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("/trips"))
                .flatMap(entry -> entry.getValue().readOperations().stream())
                .toList();
    }

    private static Operation operationOf(String operationId) {
        return tripOperations().stream()
                .filter(operation -> operationId.equals(operation.getOperationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no operation " + operationId));
    }

    /**
     * The component name of an operation's response body. Resolved schemas keep {@code $ref} when
     * the target is a named component, so both spellings are handled — the same helper
     * {@code OpenApiSpecTest} uses.
     */
    private static String responseSchemaOf(String operationId, String status) {
        Schema<?> schema = operationOf(operationId).getResponses().get(status)
                .getContent().get("application/json").getSchema();
        String ref = schema.get$ref();
        return ref == null ? schema.getName() : ref.substring(ref.lastIndexOf('/') + 1);
    }

    /**
     * Property names flattened through {@code allOf}. The versioned request bodies compose
     * {@code ExpectedVersion}, so a check that read only the outer object's own properties would
     * pass while the composed half published nothing at all.
     */
    private static List<String> propertyNamesOf(String schemaName) {
        return flattened(spec.getComponents().getSchemas().get(schemaName));
    }

    private static List<String> flattened(Schema<?> schema) {
        List<String> names = new ArrayList<>();
        if (schema == null) {
            return names;
        }
        if (schema.get$ref() != null) {
            // The parser keeps `$ref` on an allOf member rather than inlining the target, so a
            // composition has to be followed to be read at all.
            String ref = schema.get$ref();
            return flattened(spec.getComponents().getSchemas()
                    .get(ref.substring(ref.lastIndexOf('/') + 1)));
        }
        if (schema.getProperties() != null) {
            names.addAll(schema.getProperties().keySet());
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(part -> names.addAll(flattened(part)));
        }
        return names;
    }
}
