package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class PlannerSessionTest {

    private static final Instant NOW = Instant.parse("2026-07-29T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void aNewSessionIsOpenAndCarriesItsOwner() {
        PlannerSession session = PlannerSession.open(OWNER, NOW);

        assertThat(session.id()).isNotNull();
        assertThat(session.userId()).isEqualTo(OWNER);
        assertThat(session.isOpen()).isTrue();
        assertThat(session.endedAtIfPresent()).isEmpty();
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void isOwnedByAnswersTheUserScopingQuestion() {
        PlannerSession session = PlannerSession.open(OWNER, NOW);

        assertThat(session.isOwnedBy(OWNER)).isTrue();
        assertThat(session.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void endingStampsTheHandoffInstantAndProducesANewInstance() {
        PlannerSession open = PlannerSession.open(OWNER, NOW);
        Instant later = NOW.plusSeconds(600);

        PlannerSession ended = open.end(later);

        assertThat(open.isOpen()).describedAs("the original must not be mutated").isTrue();
        assertThat(ended.isOpen()).isFalse();
        assertThat(ended.endedAtIfPresent()).contains(later);
        assertThat(ended.updatedAt()).isEqualTo(later);
        assertThat(ended.createdAt()).isEqualTo(NOW);
        assertThat(ended.id()).isEqualTo(open.id());
    }

    @Test
    void endingTwiceIsRefusedRatherThanMovingTheHandoffInstant() {
        // Closing twice is not harmless: it would misreport when the handoff happened, which is the
        // one fact ended_at exists to record.
        PlannerSession ended = PlannerSession.open(OWNER, NOW).end(NOW.plusSeconds(5));

        assertThatThrownBy(() -> ended.end(NOW.plusSeconds(60)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aClockThatMovedBackwardsCannotEndASessionBeforeItBegan() {
        // NTP correction, or a second instance with a slightly different clock. Clamping is right:
        // the caller did nothing wrong and the invariant is the thing worth preserving.
        PlannerSession ended = PlannerSession.open(OWNER, NOW).end(NOW.minusSeconds(30));

        assertThat(ended.endedAtIfPresent()).contains(NOW);
        assertThat(ended.isOpen()).isFalse();
    }

    @Test
    void aSessionThatEndedBeforeItBeganIsRejectedAtConstruction() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new PlannerSession(id, OWNER, NOW.minusSeconds(1), NOW, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void everyIdentifyingFieldIsRequired() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new PlannerSession(null, OWNER, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlannerSession(id, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlannerSession(id, OWNER, null, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlannerSession(id, OWNER, null, NOW, null))
                .isInstanceOf(NullPointerException.class);
    }
}
