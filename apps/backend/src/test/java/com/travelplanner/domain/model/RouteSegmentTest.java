package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class RouteSegmentTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();
    private static final UUID FROM_AREA = UUID.randomUUID();
    private static final UUID TO_AREA = UUID.randomUUID();

    @Test
    void distinguishesACuratedLegFromAnInferredOne() {
        // estimated() is the honesty flag: without it a mode heuristic and an authored fact are
        // indistinguishable by the time they reach an itinerary.
        RouteSegment curated = segment(Duration.ofMinutes(18), false, "Use the Hachiko exit.");
        RouteSegment inferred = segment(Duration.ofMinutes(30), true, null);

        assertThat(curated.estimated()).isFalse();
        assertThat(curated.notesIfPresent()).contains("Use the Hachiko exit.");
        assertThat(inferred.estimated()).isTrue();
        assertThat(inferred.notesIfPresent()).isEmpty();
        assertThat(curated.fromAreaId()).isEqualTo(FROM_AREA);
        assertThat(curated.toAreaId()).isEqualTo(TO_AREA);
        assertThat(curated.provenance()).isEqualTo(PROVENANCE);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 18, 90, 1440})
    void reportsTheStoredMinuteCountExactly(long minutes) {
        assertThat(segment(Duration.ofMinutes(minutes), false, null).durationMinutes())
                .isEqualTo(minutes);
    }

    @Test
    void rejectsASegmentThatStartsAndEndsInTheSameArea() {
        // A self-referential leg surfaces as a zero-length hop in an itinerary rather than as the
        // data-entry slip it is.
        UUID area = UUID.randomUUID();

        assertThatThrownBy(() -> new RouteSegment(UUID.randomUUID(), UUID.randomUUID(), area, area,
                UUID.randomUUID(), Duration.ofMinutes(10), false, null, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void rejectsAZeroOrNegativeDuration() {
        assertThatThrownBy(() -> segment(Duration.ZERO, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration must be positive");
        assertThatThrownBy(() -> segment(Duration.ofMinutes(-5), false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration must be positive");
    }

    @Test
    void rejectsADurationCarryingSecondsOrNanosItCouldNotStore() {
        // The column is `duration_minutes integer`, so a sub-minute component would be truncated
        // on write and the value read back would not equal the value the caller supplied.
        assertThatThrownBy(() -> segment(Duration.ofSeconds(90), false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number of minutes");
        assertThatThrownBy(() -> segment(Duration.ofMinutes(5).plusNanos(1), false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number of minutes");
        assertThatThrownBy(() -> segment(Duration.ofSeconds(30), false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();
        Duration ten = Duration.ofMinutes(10);

        assertThatThrownBy(() -> new RouteSegment(null, id, FROM_AREA, TO_AREA, id, ten,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, null, FROM_AREA, TO_AREA, id, ten,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, id, null, TO_AREA, id, ten,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, id, FROM_AREA, null, id, ten,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, id, FROM_AREA, TO_AREA, null, ten,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, id, FROM_AREA, TO_AREA, id, null,
                false, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RouteSegment(id, id, FROM_AREA, TO_AREA, id, ten,
                false, null, null)).isInstanceOf(NullPointerException.class);
    }

    private static RouteSegment segment(Duration duration, boolean estimated, String notes) {
        return new RouteSegment(UUID.randomUUID(), UUID.randomUUID(), FROM_AREA, TO_AREA,
                UUID.randomUUID(), duration, estimated, notes, PROVENANCE);
    }
}
