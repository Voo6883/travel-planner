package com.travelplanner.domain.algorithm.scheduling;

import com.travelplanner.domain.model.Itinerary;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of scheduling a whole trip: the plan when there is one, and why when there is not.
 *
 * <p><strong>The stable interface task 30 submits candidate plans through</strong> (task 28 DoD).
 * An agent proposes places; this says whether the days they imply can be walked, and hands back
 * either an {@link Itinerary} or the reasons it could not build one. The agent never decides
 * feasibility, and nothing it writes can make an infeasible plan look ready.
 *
 * @param itinerary absent exactly when {@link #outcome()} is {@code INFEASIBLE}
 */
public record SchedulingResult(
        Itinerary itinerary,
        List<DayPlan> dayPlans,
        List<SchedulingNotice> notices) {

    public SchedulingResult {
        dayPlans = List.copyOf(Objects.requireNonNull(dayPlans, "dayPlans"));
        notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
    }

    /**
     * Aggregates the days into one answer, taking the worst of them.
     *
     * <p>A trip whose second day is empty is not a feasible trip with a small gap — it is a plan
     * that will be opened on the wrong morning. {@code Itinerary} refuses to be {@code READY} with
     * an empty day for the same reason, so this and that rule cannot disagree.
     */
    public DayPlan.Outcome outcome() {
        if (itinerary == null || dayPlans.isEmpty()) {
            return DayPlan.Outcome.INFEASIBLE;
        }
        if (dayPlans.stream().anyMatch(day -> day.outcome() == DayPlan.Outcome.INFEASIBLE)) {
            return DayPlan.Outcome.INFEASIBLE;
        }
        boolean anyPartial = dayPlans.stream()
                .anyMatch(day -> day.outcome() == DayPlan.Outcome.PARTIAL);
        return anyPartial || !notices.isEmpty() ? DayPlan.Outcome.PARTIAL : DayPlan.Outcome.FEASIBLE;
    }

    /** Absent exactly when the trip could not be scheduled at all. */
    public Optional<Itinerary> itineraryIfBuilt() {
        return Optional.ofNullable(itinerary);
    }

    /** True when any day rests on hours nobody curated (task 28 DoD: unknown data is explicit). */
    public boolean hasUnknownData() {
        return dayPlans.stream().anyMatch(DayPlan::hasUnknownData);
    }

    /** Every notice from every day, plus the trip-level ones, in day order. */
    public List<SchedulingNotice> allNotices() {
        List<SchedulingNotice> all = new java.util.ArrayList<>(notices);
        dayPlans.forEach(day -> all.addAll(day.notices()));
        return List.copyOf(all);
    }

    static SchedulingResult infeasible(List<DayPlan> dayPlans, List<SchedulingNotice> notices) {
        return new SchedulingResult(null, dayPlans, notices);
    }
}
