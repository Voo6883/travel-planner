package com.travelplanner.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.travelplanner.api.dto.VersionedMutation;
import com.travelplanner.api.dto.page.PageMetadata;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.domain.exception.DomainException;
import com.travelplanner.domain.exception.VersionConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tests for the shared primitives this task publishes: the error envelope, the
 * {@code expected_version} convention, and pagination validation.
 *
 * <p>They run against a fixture controller rather than a product endpoint because no C1–C5
 * endpoint exists yet — and must not be invented here. The behaviour under test is the platform's,
 * not any feature's, so a fixture is the honest way to pin it down before the features arrive.
 *
 * <p>Standalone MockMvc keeps the suite Docker-free and database-free. The snake_case converter
 * mirrors {@code application.yml}; {@code JsonWireFormatTest} proves the real application is
 * configured the same way.
 */
class ErrorContractTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Fixture())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                    JsonMapper.builder()
                            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                            .build()))
            .build();

    @Test
    void versionMismatchReturns409WithTheCurrentVersion() throws Exception {
        // ADR 008: the loser must be able to re-read and retry, which requires the server version.
        mockMvc.perform(post("/fixture/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.current_version").value(9));
    }

    @Test
    void missingExpectedVersionIs400ValidationFailedAndNeverAForceOverwrite() throws Exception {
        mockMvc.perform(post("/fixture/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.expected_version").exists());
    }

    @Test
    void validationDetailsUseTheWireFieldNameNotTheJavaPropertyName() throws Exception {
        mockMvc.perform(post("/fixture/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.details.fields.expectedVersion").doesNotExist());
    }

    @Test
    void malformedJsonIs400NotAServerFault() throws Exception {
        mockMvc.perform(post("/fixture/versioned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void anUnregisteredCodeIsDowngradedRatherThanLeaked() throws Exception {
        // A code the contract does not publish cannot be translated by the frontend, so the API
        // must not emit it — even when a developer invents one in a domain exception.
        mockMvc.perform(get("/fixture/unregistered"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void pageSizeAboveTheCeilingIsRejectedRatherThanClamped() throws Exception {
        mockMvc.perform(get("/fixture/paged").param("page_size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.page_size").exists());
    }

    @Test
    void paginationDefaultsAreAppliedAndEchoedInSnakeCase() throws Exception {
        mockMvc.perform(get("/fixture/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.page_size").value(20))
                .andExpect(jsonPath("$.total").value(142));
    }

    @Test
    void anUnknownSortFieldIsRejected() throws Exception {
        mockMvc.perform(get("/fixture/paged").param("sort", "DROP TABLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.sort").exists());
    }

    /** Fixture only — never a product endpoint, and never registered outside this test. */
    @RestController
    static class Fixture {

        @PostMapping("/fixture/versioned")
        Map<String, Object> versioned(@Valid @RequestBody FixtureRequest request) {
            throw new VersionConflictException(9);
        }

        @GetMapping("/fixture/unregistered")
        Map<String, Object> unregistered() {
            throw new UnregisteredException();
        }

        @GetMapping("/fixture/paged")
        PageMetadata paged(@RequestParam(required = false) Integer page,
                @RequestParam(name = "page_size", required = false) Integer pageSize,
                @RequestParam(required = false) String sort) {
            return PageMetadata.of(PageQuery.of(page, pageSize, sort), 142L);
        }
    }

    record FixtureRequest(@NotNull Integer expectedVersion) implements VersionedMutation {
    }

    static final class UnregisteredException extends DomainException {
        private static final long serialVersionUID = 1L;

        UnregisteredException() {
            super("code_nobody_registered", "should never reach a client", Map.of("leak", "no"));
        }
    }
}
