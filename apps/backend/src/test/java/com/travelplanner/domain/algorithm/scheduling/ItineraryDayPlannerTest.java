package com.travelplanner.domain.algorithm.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.DayWindow;
import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.MealSlot;
import com.travelplanner.domain.algorithm.scheduling.DayPlanRequest.PacePolicy;
import com.travelplanner.domain.algorithm.scheduling.SchedulingNotice.Reason;
import com.travelplanner.domain.enums.ItineraryItemCategory;
import com.travelplanner.domain.model.ItineraryItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The scheduling rules, as a table (task 28: "table-driven tests for overlaps, closed POIs, missing
 * hours, single/multi-day, timezone, daylight/date boundaries, duplicates, pace, and impossible
 * plans").
 *
 * <p>Identifiers are supplied by a counter rather than {@code UUID.randomUUID}, so a plan is
 * byte-for-byte reproducible and {@link #producesTheSamePlanForTheSameInputs()} means something.
 */
class ItineraryDayPlannerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 4, 1);

    @Test
    void placesEveryCandidateInPriorityOrderWithinTheDayWindow() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Senso-ji", 90, LocalTime.of(9, 0), LocalTime.of(17, 0), 1),
                        sight("Meiji Shrine", 60, LocalTime.of(9, 0), LocalTime.of(17, 0), 2)),
                List.of()));

        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.FEASIBLE);
        assertThat(plan.items()).extracting(ItineraryItem::title)
                .containsExactly("Senso-ji", "Meiji Shrine");
        assertThat(plan.items()).extracting(ItineraryItem::startsAt)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 30));
    }

    /** Ordinals are the key task 29's legs will join on, so gapless and in clock order. */
    @Test
    void numbersItemsGaplesslyInClockOrder() {
        DayPlan plan = planner().plan(request(
                List.of(sight("A", 60, null, null, 1), sight("B", 60, null, null, 2),
                        sight("C", 60, null, null, 3)),
                List.of()));

        assertThat(plan.items()).extracting(ItineraryItem::ordinal).containsExactly(0, 1, 2);
    }

    // ---------------------------------------------------------------------------- opening hours

    /**
     * The buffer is applied before the opening time, not after. Leaving at 09:00 and walking 15
     * minutes to a place that opens at 10:00 means waiting, so the block starts at 10:00 — not at
     * 09:15, which would schedule an arrival before the doors open and call it feasible.
     */
    @Test
    void waitsForOpeningRatherThanArrivingBeforeTheDoorsOpen() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Late opener", 60, LocalTime.of(10, 0), LocalTime.of(17, 0), 1, 15)),
                List.of()));

        assertThat(plan.items()).singleElement()
                .extracting(ItineraryItem::startsAt).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void refusesAPoiWhoseHoursDoNotMeetTheDayAtAll() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Night market", 60, LocalTime.of(21, 0), LocalTime.of(23, 30), 1)),
                List.of()));

        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .containsExactly(Reason.CLOSED_ALL_DAY);
    }

    /** Open, but not for long enough to fit the visit before closing. */
    @Test
    void refusesAVisitThatWouldRunPastClosingTime() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Closes early", 120, LocalTime.of(9, 0), LocalTime.of(10, 0), 1)),
                List.of()));

        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .containsExactly(Reason.NO_OPEN_SLOT);
    }

    /**
     * ADR 010 §1's corpus is PARTIAL for every sample destination. Refusing everything with unknown
     * hours would return an empty plan and call it infeasible, which is a worse answer than a plan
     * carrying a visible caveat — so the block is placed and the uncertainty reported.
     */
    @Test
    void placesAPoiWithNoCuratedHoursAndSaysSo() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Uncurated hours", 60, null, null, 1)), List.of()));

        assertThat(plan.items()).hasSize(1);
        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.PARTIAL);
        assertThat(plan.hasUnknownData()).isTrue();
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .containsExactly(Reason.UNKNOWN_HOURS);
    }

    // ---------------------------------------------------------------------------------- overlaps

    /**
     * The invariant the whole feature turns on. Two 90-minute visits cannot both start at 09:00, and
     * the second is pushed rather than stacked.
     */
    @Test
    void neverPlacesTwoBlocksOverTheSameMinute() {
        DayPlan plan = planner().plan(request(
                List.of(sight("A", 90, null, null, 1), sight("B", 90, null, null, 2),
                        sight("C", 90, null, null, 3)),
                List.of()));

        List<ItineraryItem> items = plan.items();
        for (int i = 1; i < items.size(); i++) {
            assertThat(items.get(i - 1).overlaps(items.get(i)))
                    .as("%s vs %s", items.get(i - 1).title(), items.get(i).title())
                    .isFalse();
        }
    }

    /** Touching is not overlapping — a block ending at 11:00 and one starting at 11:00 are fine. */
    @Test
    void treatsAdjacentBlocksAsCompatible() {
        DayPlan plan = planner().plan(request(
                List.of(sight("A", 60, null, null, 1), sight("B", 60, null, null, 2)), List.of()));

        assertThat(plan.items().get(0).endsAt()).isEqualTo(plan.items().get(1).startsAt());
        assertThat(plan.outcome()).isNotEqualTo(DayPlan.Outcome.INFEASIBLE);
    }

    // ------------------------------------------------------------------------------------- meals

    /** UC-C3-06: the slot is reserved even when nothing can fill it, and the gap is named. */
    @Test
    void reservesAMealSlotItCannotFillRatherThanDroppingIt() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Senso-ji", 60, null, null, 1)),
                List.of(new MealSlot("Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0), 60))));

        assertThat(plan.items()).extracting(ItineraryItem::category)
                .contains(ItineraryItemCategory.FOOD);
        assertThat(plan.items())
                .filteredOn(item -> item.category() == ItineraryItemCategory.FOOD)
                .singleElement()
                .satisfies(meal -> {
                    assertThat(meal.title()).isEqualTo("Lunch");
                    assertThat(meal.poiId()).isNull();
                });
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .contains(Reason.MEAL_SLOT_UNFILLED);
    }

    @Test
    void fillsAMealSlotFromAFoodCandidate() {
        DayPlan plan = planner().plan(request(
                List.of(food("Tsukiji sushi", 60, LocalTime.of(11, 0), LocalTime.of(15, 0), 1)),
                List.of(new MealSlot("Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0), 60))));

        assertThat(plan.items()).singleElement().satisfies(meal -> {
            assertThat(meal.title()).isEqualTo("Tsukiji sushi");
            assertThat(meal.category()).isEqualTo(ItineraryItemCategory.FOOD);
            assertThat(meal.startsAt()).isEqualTo(LocalTime.of(12, 0));
        });
        assertThat(plan.notices()).isEmpty();
    }

    /** A day is planned around its meals, so sights fill the gaps the meals leave. */
    @Test
    void schedulesSightsAroundAReservedMeal() {
        DayPlan plan = planner().plan(request(
                List.of(sight("Morning temple", 120, null, null, 1),
                        sight("Afternoon garden", 120, null, null, 2)),
                List.of(new MealSlot("Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0), 60))));

        assertThat(plan.items()).extracting(ItineraryItem::title)
                .containsExactly("Morning temple", "Lunch", "Afternoon garden");
    }

    // -------------------------------------------------------------------------------------- pace

    /**
     * The remainder is reported, not dropped. A caller that cannot see what was left out cannot
     * tell a full day from a lost candidate.
     */
    @Test
    void stopsAtThePaceCeilingAndNamesWhatItLeftOut() {
        DayPlan plan = planner().plan(request(
                List.of(sight("A", 120, null, null, 1), sight("B", 120, null, null, 2),
                        sight("C", 120, null, null, 3), sight("D", 120, null, null, 4)),
                List.of(), new PacePolicy(240, 6)));

        assertThat(plan.scheduledMinutes()).isLessThanOrEqualTo(240);
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .contains(Reason.PACE_LIMIT_REACHED);
        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.PARTIAL);
    }

    @Test
    void stopsAtTheItemCeilingEvenWhenMinutesRemain() {
        DayPlan plan = planner().plan(request(
                List.of(sight("A", 30, null, null, 1), sight("B", 30, null, null, 2),
                        sight("C", 30, null, null, 3)),
                List.of(), new PacePolicy(480, 2)));

        assertThat(plan.items()).hasSize(2);
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .contains(Reason.PACE_LIMIT_REACHED);
    }

    // -------------------------------------------------------------------------------- duplicates

    /** The same POI offered twice would be visited twice — once before lunch and again after. */
    @Test
    void schedulesTheSamePoiOnlyOnce() {
        UUID poiId = UUID.randomUUID();
        DayPlan plan = planner().plan(request(
                List.of(sightWithId(poiId, "Senso-ji", 60, 1), sightWithId(poiId, "Senso-ji", 60, 2)),
                List.of()));

        assertThat(plan.items()).hasSize(1);
        assertThat(plan.notices()).extracting(SchedulingNotice::reason)
                .contains(Reason.DUPLICATE_CANDIDATE);
    }

    // ------------------------------------------------------------------------- impossible plans

    @Test
    void reportsAnEmptyDayAsInfeasibleRatherThanAsAnEmptySuccess() {
        DayPlan plan = planner().plan(request(List.of(), List.of()));

        assertThat(plan.items()).isEmpty();
        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
    }

    /** A visit longer than the day it is offered for cannot be seated anywhere. */
    @ParameterizedTest
    @CsvSource({"09:00, 17:00, 600", "09:00, 10:00, 61", "10:00, 20:00, 601"})
    void refusesAVisitLongerThanItsDayWindow(String from, String until, int visitMinutes) {
        DayPlan plan = planner().plan(new DayPlanRequest(1, DAY, null,
                new DayWindow(LocalTime.parse(from), LocalTime.parse(until)),
                List.of(sight("Too long", visitMinutes, null, null, 1)),
                List.of(), PacePolicy.standard()));

        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
    }

    /**
     * {@code LocalTime.plusMinutes} wraps at midnight, so a block that ran past 23:59 would come
     * back as an earlier time and compare as fitting. It must be refused, not wrapped.
     */
    @Test
    void refusesABlockThatWouldWrapPastMidnight() {
        DayPlan plan = planner().plan(new DayPlanRequest(1, DAY, null,
                new DayWindow(LocalTime.of(22, 0), LocalTime.of(23, 59)),
                List.of(sight("Overnight", 180, null, null, 1)),
                List.of(), PacePolicy.standard()));

        assertThat(plan.items()).isEmpty();
        assertThat(plan.outcome()).isEqualTo(DayPlan.Outcome.INFEASIBLE);
    }

    // ------------------------------------------------------------------------------ determinism

    /** The Definition of Done's first clause: same inputs, same plan. */
    @Test
    void producesTheSamePlanForTheSameInputs() {
        List<SchedulingCandidate> candidates = List.of(
                sight("A", 90, null, null, 2), sight("B", 60, null, null, 1),
                sight("C", 45, null, null, 3));

        DayPlan first = planner().plan(request(candidates, MealSlot.standard()));
        DayPlan second = planner().plan(request(candidates, MealSlot.standard()));

        assertThat(first.items()).usingRecursiveComparison().isEqualTo(second.items());
        assertThat(first.notices()).usingRecursiveComparison().isEqualTo(second.notices());
    }

    /** Ties broken by title, so an unstable input order cannot produce two different plans. */
    @Test
    void breaksPriorityTiesDeterministically() {
        DayPlan ascending = planner().plan(request(
                List.of(sight("Alpha", 60, null, null, 1), sight("Beta", 60, null, null, 1)),
                List.of()));
        DayPlan descending = planner().plan(request(
                List.of(sight("Beta", 60, null, null, 1), sight("Alpha", 60, null, null, 1)),
                List.of()));

        assertThat(ascending.items()).extracting(ItineraryItem::title)
                .isEqualTo(descending.items().stream().map(ItineraryItem::title).toList());
    }

    // ------------------------------------------------------------------------------------ setup

    /** A counter, so identifiers are reproducible and a plan can be compared to itself. */
    private static ItineraryDayPlanner planner() {
        AtomicLong counter = new AtomicLong();
        return new ItineraryDayPlanner(() -> new UUID(0L, counter.incrementAndGet()));
    }

    private static DayPlanRequest request(
            List<SchedulingCandidate> candidates, List<MealSlot> meals) {
        return request(candidates, meals, PacePolicy.standard());
    }

    private static DayPlanRequest request(
            List<SchedulingCandidate> candidates, List<MealSlot> meals, PacePolicy pace) {
        return new DayPlanRequest(1, DAY, null, DayWindow.standard(), candidates, meals, pace);
    }

    private static SchedulingCandidate sight(
            String title, int minutes, LocalTime opens, LocalTime closes, int priority) {
        return sight(title, minutes, opens, closes, priority, 0);
    }

    private static SchedulingCandidate sight(String title, int minutes, LocalTime opens,
            LocalTime closes, int priority, int travelMinutes) {
        return new SchedulingCandidate(UUID.randomUUID(), title, ItineraryItemCategory.SIGHT,
                minutes, opens, closes, null, "wikivoyage:tokyo", travelMinutes, priority);
    }

    private static SchedulingCandidate sightWithId(
            UUID poiId, String title, int minutes, int priority) {
        return new SchedulingCandidate(poiId, title, ItineraryItemCategory.SIGHT, minutes, null,
                null, null, "wikivoyage:tokyo", 0, priority);
    }

    private static SchedulingCandidate food(
            String title, int minutes, LocalTime opens, LocalTime closes, int priority) {
        return new SchedulingCandidate(UUID.randomUUID(), title, ItineraryItemCategory.FOOD,
                minutes, opens, closes, null, "wikivoyage:tokyo", 0, priority);
    }
}
