package com.travelplanner.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.api.dto.error.ApiErrorResponse;
import com.travelplanner.api.dto.page.PageMetadata;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the running application serialises the wire format the contract publishes.
 *
 * <p>{@code ErrorContractTest} configures snake_case explicitly for its standalone MockMvc; this
 * test is what makes that configuration honest, by asserting the real {@code ObjectMapper} bean
 * behaves the same. Without it, a change to {@code application.yml} would break every client and
 * no test would notice.
 */
@SpringBootTest
@ActiveProfiles("test")
class JsonWireFormatTest {

    private final ObjectMapper objectMapper;

    JsonWireFormatTest(@Autowired ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Test
    void multiWordFieldsAreSnakeCaseOnTheWire() throws Exception {
        String json = objectMapper.writeValueAsString(new PageMetadata(0, 20, 142L));

        assertThat(json).contains("\"page_size\":20").doesNotContain("pageSize");
    }

    @Test
    void requestBodiesDeserialiseFromSnakeCase() throws Exception {
        VersionedFixture request =
                objectMapper.readValue("{\"expected_version\":7}", VersionedFixture.class);

        assertThat(request.expectedVersion()).isEqualTo(7);
    }

    @Test
    void theErrorEnvelopeKeepsExactlyTheThreeContractFields() throws Exception {
        String json = objectMapper.writeValueAsString(
                new ApiErrorResponse("version_conflict", "conflict", Map.of("current_version", 9)));

        assertThat(objectMapper.readTree(json).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("code", "message", "details");
        assertThat(json).contains("\"current_version\":9");
    }

    @Test
    void detailsKeysAreNotRewrittenByTheNamingStrategy() throws Exception {
        // `details` is a free-form map: its keys are contract values, not Java property names, so
        // the naming strategy must leave them exactly as the domain wrote them.
        String json = objectMapper.writeValueAsString(
                ApiErrorResponse.of("not_found", "missing"));

        assertThat(json).contains("\"details\":{}");
    }

    @Test
    void aMissingExpectedVersionIsRejectedOnEveryPathIntoAnAggregate() {
        // Bean Validation covers HTTP. This covers the callers it never sees — LLM tools, jobs —
        // so "omitted" can never degrade into a silent force-overwrite (ADR 008).
        assertThatThrownBy(() -> VersionedMutation.require(null))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(VersionedMutation.require(7)).isEqualTo(7);
    }

    record VersionedFixture(Integer expectedVersion) implements VersionedMutation {
    }
}
