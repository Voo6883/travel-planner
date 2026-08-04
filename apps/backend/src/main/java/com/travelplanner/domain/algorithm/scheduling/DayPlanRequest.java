package com.travelplanner.domain.algorithm.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything needed to plan one day, and nothing that would let the scheduler ask a question of the
 * outside world.
 *
 * @param window the day's wall-clock bounds in the itinerary's zone. Persisted alongside the day
 *        because re-validating against a different window would silently change what "feasible"
 *        meant when the plan was built
 * @param mealSlots UC-C3-06. Reserved by clock time rather than filled by interest match: a day
 *        with no lunch is a scheduling defect, not a preference
 * @param areaId UC-C3-07's clustering. The caller groups candidates by area and asks for one day
 *        per cluster; the scheduler does not choose the clustering, it honours it
 */
public record DayPlanRequest(
        int dayNumber,
        LocalDate date,
        UUID areaId,
        DayWindow window,
        List<SchedulingCandidate> candidates,
        List<MealSlot> mealSlots,
        PacePolicy pace) {

    public DayPlanRequest {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(pace, "pace");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        mealSlots = List.copyOf(Objects.requireNonNull(mealSlots, "mealSlots"));

        if (dayNumber < 1) {
            throw new IllegalArgumentException("dayNumber must be at least 1, got " + dayNumber);
        }
    }

    /**
     * The day's opening and closing wall-clock times.
     *
     * <p>A record rather than two loose {@code LocalTime}s on the request: they are meaningless
     * apart, and every caller that has passed them in the wrong order has produced a day that
     * schedules nothing and reports no reason.
     */
    public record DayWindow(LocalTime start, LocalTime end) {

        public DayWindow {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "a day window must end after it starts, got " + start + " to " + end);
            }
        }

        /** A sensible default for a city day, and what the tests read as "an ordinary day". */
        public static DayWindow standard() {
            return new DayWindow(LocalTime.of(9, 0), LocalTime.of(20, 0));
        }

        public int minutes() {
            return (int) java.time.Duration.between(start, end).toMinutes();
        }
    }

    /**
     * A meal the day must make room for (UC-C3-06).
     *
     * <p>Held as a window rather than a fixed time, because "lunch" is somewhere between 12:00 and
     * 14:00 rather than at 12:30 exactly, and a fixed time turns a flexible constraint into an
     * infeasible one the moment a morning visit runs long.
     *
     * @param durationMinutes how long to reserve. Reserved even when no food candidate is available:
     *        an unfilled slot is honest, while dropping the slot hides a gap in the corpus
     */
    public record MealSlot(String label, LocalTime earliest, LocalTime latest, int durationMinutes) {

        public MealSlot {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(earliest, "earliest");
            Objects.requireNonNull(latest, "latest");
            if (label.isBlank()) {
                throw new IllegalArgumentException("label must not be blank");
            }
            if (latest.isBefore(earliest)) {
                throw new IllegalArgumentException(
                        "latest must not precede earliest, got " + earliest + " to " + latest);
            }
            if (durationMinutes < 1) {
                throw new IllegalArgumentException(
                        "durationMinutes must be at least 1, got " + durationMinutes);
            }
        }

        /** UC-C3-06's two P0 slots. */
        public static List<MealSlot> standard() {
            return List.of(
                    new MealSlot("Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0), 60),
                    new MealSlot("Dinner", LocalTime.of(18, 0), LocalTime.of(20, 0), 90));
        }
    }

    /**
     * How full a day may get.
     *
     * <p>The limit is on <em>committed minutes</em> rather than on a count of stops, because four
     * museums and four coffee stops are not the same day. Exceeding it does not fail the plan — it
     * stops the scheduler adding more, and the remainder is reported as
     * {@code PACE_LIMIT_REACHED} so the caller can see what was left out rather than wondering why
     * a candidate vanished.
     *
     * @param maxScheduledMinutes the ceiling on committed time, meals included
     * @param maxItems a hard stop on block count, so a day cannot become a list of twenty
     *        ten-minute stops that satisfies the minute budget and exhausts the traveller
     */
    public record PacePolicy(int maxScheduledMinutes, int maxItems) {

        public PacePolicy {
            if (maxScheduledMinutes < 1) {
                throw new IllegalArgumentException(
                        "maxScheduledMinutes must be at least 1, got " + maxScheduledMinutes);
            }
            if (maxItems < 1) {
                throw new IllegalArgumentException("maxItems must be at least 1, got " + maxItems);
            }
        }

        /** Roughly eight committed hours and six stops — an ordinary, walkable city day. */
        public static PacePolicy standard() {
            return new PacePolicy(480, 6);
        }

        public boolean allows(int scheduledMinutes, int itemCount) {
            return scheduledMinutes <= maxScheduledMinutes && itemCount <= maxItems;
        }
    }
}
