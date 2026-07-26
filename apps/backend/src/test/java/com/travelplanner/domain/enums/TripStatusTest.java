package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Locks the enum to the "Trip status" table in {@code plans/USE-CASES.md}.
 *
 * <p>The literal list below is duplicated from that table on purpose. These names are simultaneously
 * the persisted value, the wire value, and the key the frontend stepper translates, so a rename is
 * a breaking change in three places at once — and a diff on this test is the cheapest possible way
 * to make somebody notice.
 */
class TripStatusTest {

    @Test
    void matchesTheUseCaseTableExactlyIncludingArchived() {
        assertThat(TripStatus.values()).extracting(Enum::name).containsExactly(
                "DRAFT",
                "BRIEF_COMPLETE",
                "CLARIFICATION_NEEDED",
                "RESEARCH_QUEUED",
                "RESEARCH_RUNNING",
                "RESEARCH_READY",
                "DESTINATION_SELECTED",
                "ITINERARY_READY",
                "BOOKING_IN_PROGRESS",
                "BOOKED",
                "ARCHIVED");
    }

    @Test
    void archivedIsTheOnlyReadOnlyStatus() {
        assertThat(TripStatus.values())
                .filteredOn(TripStatus::isReadOnly)
                .containsExactly(TripStatus.ARCHIVED);
    }
}
