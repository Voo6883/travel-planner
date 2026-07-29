package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Task 18 owns three statuses; everything else is refused rather than quietly accepted. */
class TripStatusTransitionTest {

    @Test
    void theIntakePhaseIsExactlyTheThreeStatusesTaskEighteenOwns() {
        assertThat(TripStatusTransition.INTAKE_STATUSES).containsExactlyInAnyOrder(
                TripStatus.DRAFT, TripStatus.CLARIFICATION_NEEDED, TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void everyMoveBetweenIntakeStatusesIsLegalIncludingUncompletingABrief() {
        // BRIEF_COMPLETE -> CLARIFICATION_NEEDED matters most: an edit that removes a field
        // un-completes the brief, and refusing the move would leave the trip claiming a
        // completeness it no longer has.
        for (TripStatus from : TripStatusTransition.INTAKE_STATUSES) {
            for (TripStatus to : TripStatusTransition.INTAKE_STATUSES) {
                assertThatCode(() -> TripStatusTransition.require(from, to))
                        .describedAs("%s -> %s", from, to)
                        .doesNotThrowAnyException();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TripStatus.class)
    void onlyTheThreeIntakeStatusesAreRecognised(TripStatus status) {
        assertThat(TripStatusTransition.isIntakeStatus(status))
                .isEqualTo(TripStatusTransition.INTAKE_STATUSES.contains(status));
    }

    @Test
    void nothingIsAnIntakeStatus() {
        assertThat(TripStatusTransition.isIntakeStatus(null)).isFalse();
    }

    @Test
    void aTripThatHasLeftIntakeCannotBeDraggedBackIntoIt() {
        assertThatThrownBy(() ->
                TripStatusTransition.require(TripStatus.RESEARCH_READY, TripStatus.DRAFT))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() ->
                TripStatusTransition.require(TripStatus.ARCHIVED, TripStatus.BRIEF_COMPLETE))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void noIntakeWriteMayPushATripForwardIntoAPhaseItDoesNotOwn() {
        // Accepting this would let a client skip the C2 gate by setting the field directly.
        Arrays.stream(TripStatus.values())
                .filter(status -> !TripStatusTransition.isIntakeStatus(status))
                .forEach(status -> assertThatThrownBy(() ->
                        TripStatusTransition.require(TripStatus.BRIEF_COMPLETE, status))
                        .describedAs("BRIEF_COMPLETE -> %s", status)
                        .isInstanceOf(ValidationFailedException.class));
    }
}
