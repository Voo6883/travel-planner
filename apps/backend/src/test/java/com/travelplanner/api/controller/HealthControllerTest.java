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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice tests for the Phase 0a health surface (PLAN §4.0.0). */
@WebMvcTest(HealthController.class)
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
