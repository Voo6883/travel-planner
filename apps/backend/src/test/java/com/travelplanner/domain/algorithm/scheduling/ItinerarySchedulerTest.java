package com.travelplanner.domain.algorithm.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.DayWindow;
import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.MealSlot;
import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.PacePolicy;
import com.travelplanner.domain.algorithm.scheduling.ItineraryScheduler.TripPlanRequest;
import com.travelplanner.domain.enums.ItineraryItemCategory;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryDay;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Whole-trip scheduling: the single/multi-day, date-boundary and publishability cases from task 28's
 * test matrix.
 */
class ItinerarySchedulerTest {

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ID = UUID.randomUUID();

    @Test
    void schedulesASingleDayTripAsAReadyPlan() {
        SchedulingResult result = scheduler().schedule(
                trip(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1)));

        assertThat(result.outcome()).isNotEqualTo(DayPlan.Outcome.INFEASIBLE);
        Itinerary itinerary = result.itineraryIfBuilt().orElseThrow();
        assertThat(itinerary.days()).hasSize(1);
        assertThat(itinerary.isPublishable()).isTrue();
        assertThat(itinerary.nightCount()).isZero();
    }

    @Test
    void schedulesAMultiDayTripWithContiguousNumberedDays() {
        SchedulingResult result = scheduler().schedule(
                trip(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 4)));

        Itinerary itinerary = result.itineraryIfBuilt().orElseThrow();
        assertThat(itinerary.days()).extracting(ItineraryDay::dayNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(itinerary.days()).extracting(ItineraryDay::date).containsExactly(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4));
        assertThat(itinerary.nightCount()).isEqualTo(3);
    }

    /**
     * Month and year boundaries, and a leap day. This is where arithmetic that assumed 30-day months
     * puts day 3 on the wrong date — the failure is invisible in April and obvious in February.
     */
    @ParameterizedTest
    @CsvSource({
        "2026-01-30, 2026-02-02",
        "2026-02-27, 2026-03-02",
        "2026-12-30, 2027-01-02",
        "2028-02-27, 2028-03-01",
    })
    void keepsDayDatesCorrectAcrossMonthAndYearBoundaries(String from, String to) {
        LocalDate start = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);

        Itinerary itinerary = scheduler().schedule(trip(start, end)).itineraryIfBuilt().orElseThrow();

        for (int i = 0; i < itinerary.days().size(); i++) {
            assertThat(itinerary.days().get(i).date()).isEqualTo(start.plusDays(i));
        }
        assertThat(itinerary.days().get(itinerary.days().size() - 1).date()).isEqualTo(end);
    }

    /**
     * A DST transition changes the length of a day but not its identity. The plan holds wall-clock
     * times in the destination's zone, so 09:00 stays 09:00 on the day the clocks move — which is
     * the whole reason the times are not instants.
     */
    @Test
    void isUnaffectedByADaylightSavingTransitionInTheDestinationZone() {
        // 2026-03-29 is when European clocks jump forward; Lisbon loses an hour that morning.
        Itinerary itinerary = scheduler().schedule(trip(
                LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 30), "Europe/Lisbon"))
                .itineraryIfBuilt().orElseThrow();

        assertThat(itinerary.days()).extracting(ItineraryDay::date).containsExactly(
                LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30));
        assertThat(itinerary.days()).allSatisfy(day ->
                assertThat(day.items().get(0).startsAt()).isEqualTo(LocalTime.of(9, 0)));
        assertThat(itinerary.zone().getId()).isEqualTo("Europe/Lisbon");
    }

    /**
     * A trip with a hole is not a shorter trip — it is a morning the traveller opens to a blank
     * page. The whole plan is refused rather than published with one day missing.
     */
    @Test
    void refusesTheWholeTripWhenAnyDayCannotBeFilled() {
        List<DayPlanRequest> days = new ArrayList<>();
        days.add(dayRequest(1, LocalDate.of(2026, 4, 1), candidates()));
        days.add(dayRequest(2, LocalDate.of(2026, 4, 2), List.of()));

        SchedulingResult result = scheduler().schedule(new TripPlanRequest(TRIP_ID, USER_ID,
                DESTINATION_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), "Asia/Tokyo",
                days));

        assertThat(result.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
        assertThat(result.itineraryIfBuilt()).isEmpty();
        // The day plans are still returned, so the caller can see which day failed and why.
        assertThat(result.dayPlans()).hasSize(2);
        assertThat(result.dayPlans().get(1).outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
    }

    /** Unknown hours make a trip PARTIAL, never INFEASIBLE, and the fact reaches the caller. */
    @Test
    void reportsATripBuiltOnUncuratedHoursAsPartialRatherThanFeasible() {
        SchedulingResult result = scheduler().schedule(trip(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1)));

        assertThat(result.hasUnknownData()).isTrue();
        assertThat(result.outcome()).isEqualTo(DayPlan.Outcome.PARTIAL);
        assertThat(result.allNotices()).isNotEmpty();
    }

    /** A malformed request is a caller bug, unlike an infeasible one, so it throws. */
    @Test
    void refusesADayCountThatDoesNotMatchTheDateRange() {
        assertThatThrownBy(() -> new TripPlanRequest(TRIP_ID, USER_ID, DESTINATION_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3),
                "Asia/Tokyo", List.of(dayRequest(1, LocalDate.of(2026, 4, 1), candidates()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 day requests for a 3-day range");
    }

    @Test
    void refusesADayWhoseDateDoesNotMatchItsOrdinal() {
        assertThatThrownBy(() -> new TripPlanRequest(TRIP_ID, USER_ID, DESTINATION_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), "Asia/Tokyo",
                List.of(dayRequest(1, LocalDate.of(2026, 4, 1), candidates()),
                        dayRequest(2, LocalDate.of(2026, 4, 9), candidates()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("should fall on 2026-04-02");
    }

    /** The version is stamped on the plan so one built by an older scheduler is identifiable. */
    @Test
    void stampsTheAlgorithmVersionOnThePlan() {
        Itinerary itinerary = scheduler()
                .schedule(trip(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1)))
                .itineraryIfBuilt().orElseThrow();

        assertThat(itinerary.algorithmVersion()).isEqualTo(ItineraryScheduler.ALGORITHM_VERSION);
    }

    // ------------------------------------------------------------------------------------ setup

    private static ItineraryScheduler scheduler() {
        AtomicLong counter = new AtomicLong();
        java.util.function.Supplier<UUID> ids = () -> new UUID(0L, counter.incrementAndGet());
        return new ItineraryScheduler(new ItineraryDayPlanner(ids), ids);
    }

    private static TripPlanRequest trip(LocalDate start, LocalDate end) {
        return trip(start, end, "Asia/Tokyo");
    }

    private static TripPlanRequest trip(LocalDate start, LocalDate end, String timezone) {
        List<DayPlanRequest> days = new ArrayList<>();
        long span = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        for (int i = 0; i < span; i++) {
            days.add(dayRequest(i + 1, start.plusDays(i), candidates()));
        }
        return new TripPlanRequest(TRIP_ID, USER_ID, DESTINATION_ID, start, end, timezone, days);
    }

    private static DayPlanRequest dayRequest(
            int dayNumber, LocalDate date, List<SchedulingCandidate> candidates) {
        return new DayPlanRequest(dayNumber, date, null, DayWindow.standard(), candidates,
                List.of(new MealSlot("Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0), 60)),
                PacePolicy.standard());
    }

    private static List<SchedulingCandidate> candidates() {
        return List.of(new SchedulingCandidate(UUID.randomUUID(), "Senso-ji",
                ItineraryItemCategory.SIGHT, 90, null, null, null, "wikivoyage:tokyo", 0, 1));
    }
}
