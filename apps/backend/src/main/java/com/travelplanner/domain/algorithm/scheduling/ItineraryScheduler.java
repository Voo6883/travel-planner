package com.travelplanner.domain.algorithm.scheduling;

import com.travelplanner.domain.enums.ItineraryStatus;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Schedules a whole trip, day by day, and assembles the aggregate (UC-C3-01, task 28 DoD).
 *
 * <p>Thin on purpose: {@link ItineraryDayPlanner} holds the placement rules, and this owns the two
 * decisions that only exist across days — whether the result may be published, and what to do when
 * one day cannot be filled.
 *
 * <p><strong>Date arithmetic goes through {@code LocalDate.plusDays}, never through a day count.</strong>
 * Month boundaries and DST transitions are where hand-rolled arithmetic produces a plan whose day 3
 * falls on the wrong date, and the itinerary's own constructor checks the result of this so the two
 * cannot disagree.
 */
public final class ItineraryScheduler {

    /** Persisted on every plan, so one built by an older scheduler is identifiable. */
    public static final String ALGORITHM_VERSION = ItineraryDayPlanner.ALGORITHM_VERSION;

    private final ItineraryDayPlanner dayPlanner;
    private final Supplier<UUID> ids;

    public ItineraryScheduler(ItineraryDayPlanner dayPlanner, Supplier<UUID> ids) {
        this.dayPlanner = Objects.requireNonNull(dayPlanner, "dayPlanner");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public ItineraryScheduler() {
        this(new ItineraryDayPlanner(), UUID::randomUUID);
    }

    /**
     * Builds a plan, or explains why it could not.
     *
     * <p>Returns {@code INFEASIBLE} rather than throwing when a day comes out empty. An unschedulable
     * trip is an ordinary answer on a PARTIAL corpus — the destination simply has too little curated
     * — and an exception would make the caller treat a data gap as a defect.
     *
     * @throws IllegalArgumentException only for a request that is malformed rather than infeasible:
     *         a day count that does not match the date range
     */
    public SchedulingResult schedule(TripPlanRequest request) {
        Objects.requireNonNull(request, "request");

        List<DayPlan> dayPlans = new ArrayList<>(request.days().size());
        List<ItineraryDay> days = new ArrayList<>(request.days().size());
        List<SchedulingNotice> tripNotices = new ArrayList<>();

        for (DayPlanRequest dayRequest : request.days()) {
            DayPlan plan = dayPlanner.plan(dayRequest);
            dayPlans.add(plan);
            if (plan.outcome() == DayPlan.Outcome.INFEASIBLE) {
                tripNotices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.DAY_WINDOW_EXHAUSTED,
                        "Day " + dayRequest.dayNumber()));
                continue;
            }
            days.add(new ItineraryDay(ids.get(), plan.dayNumber(), plan.date(), plan.areaId(),
                    plan.window().start(), plan.window().end(), plan.items()));
        }

        // Every day must have produced something. A plan with a hole is not a shorter trip; it is a
        // morning the traveller opens to a blank page, and Itinerary refuses to publish it anyway.
        if (days.size() != request.days().size() || days.isEmpty()) {
            return SchedulingResult.infeasible(List.copyOf(dayPlans), List.copyOf(tripNotices));
        }

        Itinerary itinerary = new Itinerary(ids.get(), request.tripId(), request.userId(),
                request.destinationId(), ItineraryStatus.READY, request.startDate(),
                request.endDate(), request.timezone(), ALGORITHM_VERSION, days, 0);
        return new SchedulingResult(itinerary, List.copyOf(dayPlans), List.copyOf(tripNotices));
    }

    /**
     * One trip's worth of scheduling input.
     *
     * <p>The day requests are supplied rather than derived: clustering days by area is UC-C3-07, and
     * the caller does it with knowledge-base data this package deliberately cannot reach.
     */
    public record TripPlanRequest(
            UUID tripId,
            UUID userId,
            UUID destinationId,
            LocalDate startDate,
            LocalDate endDate,
            String timezone,
            List<DayPlanRequest> days) {

        public TripPlanRequest {
            Objects.requireNonNull(tripId, "tripId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(destinationId, "destinationId");
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(endDate, "endDate");
            Objects.requireNonNull(timezone, "timezone");
            days = List.copyOf(Objects.requireNonNull(days, "days"));

            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException(
                        "endDate must not precede startDate, got " + startDate + " to " + endDate);
            }
            long span = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days.size() != span) {
                throw new IllegalArgumentException("got " + days.size() + " day requests for a "
                        + span + "-day range " + startDate + " to " + endDate);
            }
            for (int i = 0; i < days.size(); i++) {
                DayPlanRequest day = days.get(i);
                if (day.dayNumber() != i + 1) {
                    throw new IllegalArgumentException("day requests must run 1.." + days.size()
                            + " in order, found " + day.dayNumber() + " at position " + (i + 1));
                }
                LocalDate expected = startDate.plusDays(i);
                if (!day.date().equals(expected)) {
                    throw new IllegalArgumentException("day " + day.dayNumber()
                            + " should fall on " + expected + ", got " + day.date());
                }
            }
        }
    }
}
