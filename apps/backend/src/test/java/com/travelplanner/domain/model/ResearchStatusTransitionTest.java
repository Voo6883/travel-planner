package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

/** The four C2 trip-status moves, and the refusals that keep a client from skipping the C2 gate. */
class ResearchStatusTransitionTest {

    @Test
    void researchStartsOnlyFromACompleteBrief() {
        assertThat(ResearchStatusTransition.requireStart(TripStatus.BRIEF_COMPLETE))
                .isEqualTo(TripStatus.RESEARCH_QUEUED);
    }

    @Test
    void startingFromAnyOtherStatusIsRefused() {
        assertThatThrownBy(() -> ResearchStatusTransition.requireStart(TripStatus.DRAFT))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> ResearchStatusTransition.requireStart(TripStatus.RESEARCH_RUNNING))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> ResearchStatusTransition.requireStart(TripStatus.RESEARCH_READY))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void theRunningAndReadyGuardsMatchTheSchemaFlow() {
        assertThat(ResearchStatusTransition.canBeginRunning(TripStatus.RESEARCH_QUEUED)).isTrue();
        assertThat(ResearchStatusTransition.canBeginRunning(TripStatus.BRIEF_COMPLETE)).isFalse();
        assertThat(ResearchStatusTransition.canBecomeReady(TripStatus.RESEARCH_RUNNING)).isTrue();
        assertThat(ResearchStatusTransition.canBecomeReady(TripStatus.RESEARCH_QUEUED)).isFalse();
    }

    @Test
    void recoveryReturnsAQueuedOrRunningTripToBriefComplete() {
        assertThat(ResearchStatusTransition.canRecover(TripStatus.RESEARCH_QUEUED)).isTrue();
        assertThat(ResearchStatusTransition.canRecover(TripStatus.RESEARCH_RUNNING)).isTrue();
        assertThat(ResearchStatusTransition.recover(TripStatus.RESEARCH_RUNNING))
                .isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void recoveringATripThatAlreadyMovedOnIsRefused() {
        assertThat(ResearchStatusTransition.canRecover(TripStatus.RESEARCH_READY)).isFalse();
        assertThatThrownBy(() -> ResearchStatusTransition.recover(TripStatus.RESEARCH_READY))
                .isInstanceOf(ValidationFailedException.class);
    }
}
