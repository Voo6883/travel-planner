package com.travelplanner.application.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for readiness aggregation. No Spring context required. */
class ReadinessServiceTest {

    @Test
    void readyWhenEveryContributorIsUp() {
        ReadinessService service = new ReadinessService(List.of(up("process"), up("database")));

        ReadinessStatus status = service.evaluate();

        assertThat(status.ready()).isTrue();
        assertThat(status.components()).containsEntry("process", "UP").containsEntry("database", "UP");
    }

    @Test
    void notReadyWhenAnyContributorIsDown() {
        ReadinessService service = new ReadinessService(List.of(up("process"), down("database", "unreachable")));

        ReadinessStatus status = service.evaluate();

        assertThat(status.ready()).isFalse();
        assertThat(status.components()).containsEntry("database", "DOWN: unreachable");
    }

    @Test
    void readyWithNoContributorsRegistered() {
        ReadinessStatus status = new ReadinessService(List.of()).evaluate();

        assertThat(status.ready()).isTrue();
        assertThat(status.components()).isEmpty();
    }

    @Test
    void throwingContributorIsReportedDownRatherThanPropagating() {
        ReadinessContributor exploding = new ReadinessContributor() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public ReadinessCheck check() {
                throw new IllegalStateException("connection pool exhausted");
            }
        };

        ReadinessStatus status = new ReadinessService(List.of(exploding)).evaluate();

        // Readiness must report, not fail — a 500 here would be indistinguishable from a bug.
        assertThat(status.ready()).isFalse();
        assertThat(status.components()).containsEntry("flaky", "DOWN: check failed");
    }

    private ReadinessContributor up(String name) {
        return contributor(name, ReadinessCheck.up());
    }

    private ReadinessContributor down(String name, String detail) {
        return contributor(name, ReadinessCheck.down(detail));
    }

    private ReadinessContributor contributor(String name, ReadinessCheck result) {
        return new ReadinessContributor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ReadinessCheck check() {
                return result;
            }
        };
    }
}
