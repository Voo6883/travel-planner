package com.travelplanner.domain.algorithm.scheduling;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One thing the scheduler decided that the traveller is entitled to know (task 28 DoD: "unknown
 * source data is explicit", "never silently ignore constraints").
 *
 * <p><strong>Why every dropped candidate produces one of these.</strong> A scheduler that returns
 * only the plan is indistinguishable, from the outside, from one that lost half its input to a bug.
 * The caller cannot tell "there was nothing else worth adding" from "six places were dropped
 * because their hours could not be met", and neither can the person debugging it six months later.
 *
 * @param poiId absent when the notice is about the day rather than about one candidate
 */
public record SchedulingNotice(Reason reason, String subject, UUID poiId) {

    public SchedulingNotice {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
    }

    public static SchedulingNotice about(Reason reason, SchedulingCandidate candidate) {
        return new SchedulingNotice(reason, candidate.title(), candidate.poiId());
    }

    public static SchedulingNotice about(Reason reason, String subject) {
        return new SchedulingNotice(reason, subject, null);
    }

    public Optional<UUID> poiIdIfKnown() {
        return Optional.ofNullable(poiId);
    }

    /** Whether this notice describes missing knowledge rather than a scheduling decision. */
    public boolean isUnknownData() {
        return reason == Reason.UNKNOWN_HOURS;
    }

    /** Why a candidate did not make the plan, or made it with a caveat. */
    public enum Reason {

        /**
         * Placed, but the knowledge base has no curated opening hours for it.
         *
         * <p>The block is in the plan. ADR 010 §1's corpus is PARTIAL for every sample destination,
         * so refusing everything with unknown hours would return an empty itinerary and call it
         * infeasible — which is a worse answer than a plan carrying a visible caveat.
         */
        UNKNOWN_HOURS,

        /** Its curated hours do not intersect the day window at all. */
        CLOSED_ALL_DAY,

        /** Open today, but not for long enough anywhere in the remaining gaps. */
        NO_OPEN_SLOT,

        /** The day filled up first — pace limit or item ceiling reached. */
        PACE_LIMIT_REACHED,

        /** The day window ran out before this candidate could be placed. */
        DAY_WINDOW_EXHAUSTED,

        /** UC-C3-06: a meal slot was reserved but no food candidate was available to fill it. */
        MEAL_SLOT_UNFILLED,

        /** The same POI appeared twice among the candidates; the later one was dropped. */
        DUPLICATE_CANDIDATE
    }
}
