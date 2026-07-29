package com.travelplanner.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.model.Destination;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the coverage surface (ADR 010 §4).
 *
 * <p>{@code addFilters = false} for the same reason {@code HealthControllerTest} does it: a
 * {@code @WebMvcTest} does not load the application's own {@code SecurityConfig}, so Spring Boot's
 * default "authenticate everything" chain would turn these routing assertions into 401s. That this
 * endpoint really is public is a property of {@code SecurityConfig.PUBLIC_GET} and is asserted
 * against the real configuration by the API integration suite.
 */
@WebMvcTest(DestinationController.class)
@AutoConfigureMockMvc(addFilters = false)
class DestinationControllerTest {

    private static final Destination TOKYO = new Destination(UUID.randomUUID(), "tokyo-jp", "Tokyo",
            "JP", "Asia/Tokyo", 35.6762, 139.6503, CoverageLevel.FULL);

    /** A curated destination whose position was never filled in — coordinates are optional. */
    private static final Destination BANGKOK = new Destination(UUID.randomUUID(), "bangkok-th",
            "Bangkok", "TH", "Asia/Bangkok", null, null, CoverageLevel.FULL);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportedDestinationService destinations;

    @Test
    void publishesSlugNameCountryTimezoneAndCoordinatesInSnakeCase() throws Exception {
        when(destinations.listSupported()).thenReturn(List.of(TOKYO));

        mockMvc.perform(get("/api/v1/destinations/supported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations[0].slug").value("tokyo-jp"))
                .andExpect(jsonPath("$.destinations[0].name").value("Tokyo"))
                .andExpect(jsonPath("$.destinations[0].country_code").value("JP"))
                .andExpect(jsonPath("$.destinations[0].timezone").value("Asia/Tokyo"))
                .andExpect(jsonPath("$.destinations[0].coordinates.latitude").value(35.6762))
                .andExpect(jsonPath("$.destinations[0].coordinates.longitude").value(139.6503));
    }

    /**
     * The surrogate key stays server-side. Publishing it would give clients a second way to name a
     * destination, and {@code destination_not_covered} already speaks only slugs.
     */
    @Test
    void neverPublishesTheInternalDestinationId() throws Exception {
        when(destinations.listSupported()).thenReturn(List.of(TOKYO));

        mockMvc.perform(get("/api/v1/destinations/supported"))
                .andExpect(jsonPath("$.destinations[0].id").doesNotExist())
                .andExpect(jsonPath("$.destinations[0].destination_id").doesNotExist());
    }

    /**
     * Null, and never a lone latitude. The contract publishes {@code coordinates} as a nullable
     * object precisely so a client cannot read half a position as a place on the equator.
     */
    @Test
    void reportsNoCoordinatesRatherThanInventingHalfOfThem() throws Exception {
        when(destinations.listSupported()).thenReturn(List.of(BANGKOK));

        mockMvc.perform(get("/api/v1/destinations/supported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations[0].coordinates").doesNotExist());
    }

    /**
     * An empty catalogue is a 200 with an empty array, not a 404. "We cover nothing yet" is an
     * answer the picker can render honestly; a 404 reads like a broken route. It is also what a
     * context with no datasource returns, which is how the unit suite keeps running without Docker.
     */
    @Test
    void answersWithAnEmptyListWhenNothingIsCurated() throws Exception {
        when(destinations.listSupported()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/destinations/supported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations").isArray())
                .andExpect(jsonPath("$.destinations").isEmpty());
    }
}
