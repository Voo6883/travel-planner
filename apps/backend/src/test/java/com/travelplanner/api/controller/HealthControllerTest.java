package com.travelplanner.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.health.ReadinessService;
import com.travelplanner.application.health.ReadinessStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for the Phase 0a health surface (PLAN §4.0.0).
 *
 * <p>{@code addFilters = false} since task 08 put Spring Security on the classpath. A
 * {@code @WebMvcTest} does not load the application's own {@code SecurityConfig} — only beans that
 * <em>are</em> a {@code SecurityFilterChain} — so it would otherwise apply Spring Boot's default
 * "authenticate everything" chain and these routing assertions would all become 401s. The real
 * rule, that {@code /health} and {@code /ready} are public, is asserted against the actual
 * configuration by {@code AuthApiIntegrationTest}.
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadinessService readinessService;

    @Test
    void healthReturnsUpWithoutConsultingAnyDependency() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Liveness must never depend on readiness — a down database must not restart the container.
        org.mockito.Mockito.verifyNoInteractions(readinessService);
    }

    @Test
    void readyReturns200WhenAllContributorsAreUp() throws Exception {
        when(readinessService.evaluate()).thenReturn(new ReadinessStatus(true, Map.of("process", "UP")));

        mockMvc.perform(get("/api/v1/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.process").value("UP"));
    }

    @Test
    void readyReturns503WhenAnyContributorIsDown() throws Exception {
        when(readinessService.evaluate())
                .thenReturn(new ReadinessStatus(false, Map.of("database", "DOWN: unreachable")));

        mockMvc.perform(get("/api/v1/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.database").value("DOWN: unreachable"));
    }
}
