package com.travelplanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.health.ReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the full context starts with no external dependency — the property that keeps
 * {@code ./gradlew test} runnable without Docker or a database.
 */
@SpringBootTest
@ActiveProfiles("test")
class TravelPlannerApplicationTest {

    @Autowired
    private ReadinessService readinessService;

    @Test
    void contextLoadsAndReadinessIsWired() {
        assertThat(readinessService).isNotNull();
        assertThat(readinessService.evaluate().components()).containsKey("process");
    }
}
