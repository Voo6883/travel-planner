package com.travelplanner.domain.algorithm.scheduling;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import com.travelplanner.domain.model.ItineraryItem;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Greedy day scheduling under constraints (PLAN §4.0.3 "Greedy + constraints", UC-C3-06/07/08).
 *
 * <h2>Why greedy</h2>
 *
 * <p>A day holds six or so blocks drawn from a few dozen candidates, all of them already ranked by
 * the caller. Optimal placement of that is a search nobody can see the benefit of: the difference
 * between the best schedule and a good one is a few minutes of walking, while the difference between
 * a deterministic schedule and a clever one is whether the same trip replans identically tomorrow.
 * Determinism is the property the Definition of Done names, so the algorithm is a single forward
 * pass in priority order with no backtracking.
 *
 * <h2>The order of operations, and why it is that order</h2>
 *
 * <ol>
 *   <li><strong>Meals are reserved first.</strong> UC-C3-06 makes lunch and dinner structural, and
 *       anything placed before them can only be worked around. Reserving late is how a day ends up
 *       with three temples and nothing to eat.</li>
 *   <li><strong>Sights fill the gaps between them,</strong> in the caller's priority order, each
 *       pushed past its opening time and past the travel buffer the caller supplied.</li>
 *   <li><strong>Whatever did not fit becomes a notice,</strong> never a silent omission.</li>
 * </ol>
 *
 * <p>Complexity is {@code O(n log n)} on the candidate count — the sort dominates; placement is one
 * pass over candidates against a gap list that only shrinks.
 *
 * <p><b>No clock, no I/O, no randomness.</b> Identifiers come from a supplier the caller controls,
 * so a test can make them reproducible and the same inputs always produce the same plan.
 */
public final class ItineraryDayPlanner {

    /** Bumped when placement changes in a way that would produce a different plan. */
    public static final String ALGORITHM_VERSION = "itinerary-day-planner-v1";

    private final Supplier<UUID> ids;

    /** @param ids identifier source, injected so a test can make a plan byte-for-byte reproducible */
    public ItineraryDayPlanner(Supplier<UUID> ids) {
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public ItineraryDayPlanner() {
        this(UUID::randomUUID);
    }

    /** Plans one day. Never throws for an unschedulable day — that is {@code INFEASIBLE}. */
    public DayPlan plan(DayPlanRequest request) {
        Objects.requireNonNull(request, "request");

        List<SchedulingNotice> notices = new ArrayList<>();
        List<Placement> placed = new ArrayList<>();

        List<SchedulingCandidate> candidates = deduplicate(request.candidates(), notices);
        placeMeals(request, candidates, placed, notices);
        placeSights(request, candidates, placed, notices);

        placed.sort(Comparator.comparing(Placement::start));
        return new DayPlan(request.dayNumber(), request.date(), request.areaId(), request.window(),
                toItems(placed), List.copyOf(notices));
    }

    /**
     * The same POI offered twice would be scheduled twice — a day that visits one temple in the
     * morning and again after lunch. Keeps the first occurrence, which is the higher-priority one
     * once the caller has ranked them.
     */
    private static List<SchedulingCandidate> deduplicate(
            List<SchedulingCandidate> candidates, List<SchedulingNotice> notices) {
        Set<UUID> seen = new HashSet<>();
        List<SchedulingCandidate> unique = new ArrayList<>(candidates.size());
        for (SchedulingCandidate candidate : candidates) {
            if (candidate.poiId() != null && !seen.add(candidate.poiId())) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.DUPLICATE_CANDIDATE, candidate));
                continue;
            }
            unique.add(candidate);
        }
        return unique;
    }

    /**
     * UC-C3-06. A slot is reserved whether or not a food candidate exists to fill it.
     *
     * <p>An unfilled slot carries no POI and says so through {@code MEAL_SLOT_UNFILLED}. Dropping
     * the slot instead would hide a gap in the corpus behind a day that merely looks busy — and on
     * the SAMPLE seed, where food POIs are thin, that is the difference between "we have no
     * restaurants for this area" and a plan that quietly skips dinner.
     */
    private void placeMeals(DayPlanRequest request, List<SchedulingCandidate> candidates,
            List<Placement> placed, List<SchedulingNotice> notices) {
        Set<UUID> used = usedPoiIds(placed);
        for (DayPlanRequest.MealSlot slot : request.mealSlots()) {
            LocalTime start = slot.earliest();
            LocalTime end = start.plusMinutes(slot.durationMinutes());
            if (end.isAfter(request.window().end()) || start.isBefore(request.window().start())) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.DAY_WINDOW_EXHAUSTED, slot.label()));
                continue;
            }
            SchedulingCandidate food = candidates.stream()
                    .filter(candidate -> candidate.category() == ItineraryItemCategory.FOOD)
                    .filter(candidate -> !used.contains(candidate.poiId()))
                    .filter(candidate -> candidate.isOpenThroughout(start, end))
                    .min(Comparator.comparingInt(SchedulingCandidate::priority))
                    .orElse(null);

            if (food == null) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.MEAL_SLOT_UNFILLED, slot.label()));
                placed.add(new Placement(null, slot.label(), ItineraryItemCategory.FOOD, start, end,
                        slot.durationMinutes(), null));
                continue;
            }
            if (food.poiId() != null) {
                used.add(food.poiId());
            }
            if (!food.hasKnownHours()) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.UNKNOWN_HOURS, food));
            }
            placed.add(new Placement(food.poiId(), food.title(), ItineraryItemCategory.FOOD, start,
                    end, slot.durationMinutes(), food.sourceRef()));
        }
    }

    /**
     * Fills what is left, in the caller's priority order.
     *
     * <p>Each candidate is tried against the gaps between what is already placed. The first gap it
     * fits — after its opening time and after the travel buffer — wins; a candidate that fits
     * nowhere produces the notice that says which constraint stopped it, rather than disappearing.
     */
    private void placeSights(DayPlanRequest request, List<SchedulingCandidate> candidates,
            List<Placement> placed, List<SchedulingNotice> notices) {
        List<SchedulingCandidate> queue = candidates.stream()
                .filter(candidate -> candidate.category() != ItineraryItemCategory.FOOD)
                .sorted(Comparator.comparingInt(SchedulingCandidate::priority)
                        .thenComparing(SchedulingCandidate::title))
                .toList();

        for (SchedulingCandidate candidate : queue) {
            int committed = placed.stream().mapToInt(Placement::durationMinutes).sum();
            if (!request.pace().allows(committed + candidate.visitMinutes(), placed.size() + 1)) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.PACE_LIMIT_REACHED, candidate));
                continue;
            }
            if (candidate.hasKnownHours() && !intersectsWindow(candidate, request.window())) {
                notices.add(SchedulingNotice.about(
                        SchedulingNotice.Reason.CLOSED_ALL_DAY, candidate));
                continue;
            }
            Placement placement = firstFit(candidate, request, placed);
            if (placement == null) {
                notices.add(SchedulingNotice.about(
                        candidate.hasKnownHours()
                                ? SchedulingNotice.Reason.NO_OPEN_SLOT
                                : SchedulingNotice.Reason.DAY_WINDOW_EXHAUSTED,
                        candidate));
                continue;
            }
            if (!candidate.hasKnownHours()) {
                notices.add(SchedulingNotice.about(SchedulingNotice.Reason.UNKNOWN_HOURS, candidate));
            }
            placed.add(placement);
            placed.sort(Comparator.comparing(Placement::start));
        }
    }

    /**
     * The first gap this candidate fits, scanning forward from the start of the day.
     *
     * <p>Gaps are derived from what is already placed rather than tracked incrementally: the list is
     * tiny, and a derived view cannot fall out of step with the placements the way a maintained free
     * list does after the third edit.
     */
    private Placement firstFit(
            SchedulingCandidate candidate, DayPlanRequest request, List<Placement> placed) {
        LocalTime cursor = request.window().start();
        for (Placement occupied : placed) {
            Placement fit = tryFit(candidate, cursor, occupied.start(), request);
            if (fit != null) {
                return fit;
            }
            cursor = maxOf(cursor, occupied.end());
        }
        return tryFit(candidate, cursor, request.window().end(), request);
    }

    /**
     * Attempts to seat the candidate in {@code [from, until)}.
     *
     * <p>The travel buffer is applied before the opening time, not after: leaving at 09:00 and
     * walking fifteen minutes to a place that opens at 09:30 means arriving at 09:15 and waiting,
     * so the block starts at 09:30. Applying them the other way round schedules an arrival before
     * the doors open and calls it feasible.
     */
    private Placement tryFit(SchedulingCandidate candidate, LocalTime from, LocalTime until,
            DayPlanRequest request) {
        LocalTime afterTravel = from.plusMinutes(candidate.travelMinutesFromPrevious());
        LocalTime start = candidate.earliestStartFrom(afterTravel);
        LocalTime end = start.plusMinutes(candidate.visitMinutes());

        // plusMinutes wraps at midnight; a block that wrapped would compare as earlier than its own
        // start and defeat every check below it.
        if (!end.isAfter(start)) {
            return null;
        }
        if (end.isAfter(until) || end.isAfter(request.window().end())) {
            return null;
        }
        if (start.isBefore(request.window().start())) {
            return null;
        }
        if (!candidate.isOpenThroughout(start, end)) {
            return null;
        }
        return new Placement(candidate.poiId(), candidate.title(), candidate.category(), start, end,
                candidate.visitMinutes(), candidate.sourceRef());
    }

    private static boolean intersectsWindow(
            SchedulingCandidate candidate, DayPlanRequest.DayWindow window) {
        return candidate.opensAt().isBefore(window.end())
                && window.start().isBefore(candidate.closesAt());
    }

    private static Set<UUID> usedPoiIds(List<Placement> placed) {
        Set<UUID> used = new LinkedHashSet<>();
        for (Placement placement : placed) {
            if (placement.poiId() != null) {
                used.add(placement.poiId());
            }
        }
        return used;
    }

    private static LocalTime maxOf(LocalTime left, LocalTime right) {
        return left.isAfter(right) ? left : right;
    }

    private List<ItineraryItem> toItems(List<Placement> placed) {
        List<ItineraryItem> items = new ArrayList<>(placed.size());
        for (int i = 0; i < placed.size(); i++) {
            Placement placement = placed.get(i);
            items.add(new ItineraryItem(ids.get(), i, placement.poiId(), placement.category(),
                    placement.title(), placement.start(), placement.end(),
                    placement.durationMinutes(), placement.sourceRef(), null));
        }
        return items;
    }

    /** A seated block, before it is given an identity and an ordinal. */
    private record Placement(
            UUID poiId,
            String title,
            ItineraryItemCategory category,
            LocalTime start,
            LocalTime end,
            int durationMinutes,
            String sourceRef) {
    }
}
