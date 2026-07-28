package com.travelplanner.api.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Error-envelope behaviour (PLAN §6.1) through the real application.
 *
 * <p>{@code @AutoConfigureMockMvc} rather than a manual {@code webAppContextSetup}: it registers
 * the application's {@code Filter} beans, which is what lets this test prove that
 * {@code RequestIdFilter} is actually wired into the chain and not merely instantiable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc;

    GlobalExceptionHandlerTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void unknownRouteReturns404WithTheStandardEnvelope() throws Exception {
        // Regression: the catch-all handler previously swallowed the no-handler exception and
        // turned every mistyped URL into a 500.
        //
        // Authenticated, since task 08. The security chain denies by default, so an anonymous
        // request never reaches the dispatcher — see the test below.
        mockMvc.perform(get("/api/v1/definitely-not-a-route").with(user("someone")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void anAnonymousRequestToAProtectedRouteIsUnauthorizedRatherThanNotFound() throws Exception {
        // Task 08: everything outside the published public set requires authentication, so a
        // mistyped URL answers 401 to a signed-out caller. That is the safe direction — a 404
        // would confirm which paths do not exist, and by elimination which ones do.
        mockMvc.perform(get("/api/v1/definitely-not-a-route"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void everyResponseCarriesTheRequestIdHeaderTheContractPromises() throws Exception {
        // End-to-end through the real filter chain: RequestIdFilter runs before the advice, so
        // even a failed request is traceable to its log lines (PLAN §4.0.2-J2).
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "task-06-probe"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "task-06-probe"));

        mockMvc.perform(get("/api/v1/definitely-not-a-route").with(user("someone")))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"));

        // The security chain rejects before any controller, so this proves RequestIdFilter runs
        // ahead of it — a 401 nobody can trace is a 401 nobody can debug.
        mockMvc.perform(get("/api/v1/definitely-not-a-route"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void errorResponseNeverLeaksStackTracesOrInternalPackages() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/definitely-not-a-route").with(user("someone")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("com.travelplanner")
                .doesNotContain("Exception")
                .doesNotContain("trace");
    }
}
