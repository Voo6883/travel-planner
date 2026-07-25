package com.travelplanner.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Error-envelope behaviour (PLAN §6.1). */
@SpringBootTest
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc;

    GlobalExceptionHandlerTest(@Autowired WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void unknownRouteReturns404WithTheStandardEnvelope() throws Exception {
        // Regression: the catch-all handler previously swallowed the no-handler exception and
        // turned every mistyped URL into a 500.
        mockMvc.perform(get("/api/v1/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void errorResponseNeverLeaksStackTracesOrInternalPackages() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/definitely-not-a-route"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("com.travelplanner")
                .doesNotContain("Exception")
                .doesNotContain("trace");
    }
}
