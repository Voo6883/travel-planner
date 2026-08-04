package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which {@code trip.status} moves the C2 research slice is allowed to make (tasks/23).
 *
 * <p>Deliberately a sibling of {@link TripStatusTransition} rather than an extension of it. That
 * class owns the intake vocabulary ({@code DRAFT}/{@code CLARIFICATION_NEEDED}/
 * {@code BRIEF_COMPLETE}) and its {@code require} refuses anything else — including
 * {@code RESEARCH_QUEUED} — precisely so a client cannot skip the C2 gate by writing a status
 * directly. Research is the task that owns the states beyond the gate, so it states its own moves
 * here, next to the service that performs them, and leaves the intake helper untouched.
 *
 * <p>The four moves, and why they are these four:
 *
 * <ul>
 *   <li>{@code BRIEF_COMPLETE → RESEARCH_QUEUED} — the start (UC-C2-01). The gate: research may
 *       begin only from a complete brief.</li>
 *   <li>{@code RESEARCH_QUEUED → RESEARCH_RUNNING} — a worker picked the job up (UC-C2-02).</li>
 *   <li>{@code RESEARCH_RUNNING → RESEARCH_READY} — the run produced recommendations (the
 *       completion hook task 25 fills in).</li>
 *   <li>{@code RESEARCH_QUEUED|RESEARCH_RUNNING → BRIEF_COMPLETE} — failure recovery. There is
 *       <em>no</em> trip-level failed status (the task's Do-not list, and
 *       {@code docs/PLAN-COMPATIBILITY.md}: "trip keeps last valid status"); a failed run instead
 *       returns the trip to the last state a re-run can start from, so the user is never stuck in
 *       {@code RESEARCH_RUNNING} behind a dead job (UC-C2-07).</li>
 * </ul>
 */
public final class ResearchStatusTransition {

    /** The statuses this slice reads or writes. */
    public static final Set<TripStatus> RESEARCH_STATUSES = EnumSet.of(
            TripStatus.RESEARCH_QUEUED, TripStatus.RESEARCH_RUNNING, TripStatus.RESEARCH_READY);

    private ResearchStatusTransition() {
    }

    /**
     * The start move. Research may begin only from a complete brief.
     *
     * @return {@link TripStatus#RESEARCH_QUEUED}
     * @throws ValidationFailedException {@code validation_failed} on {@code status} when the trip is
     *         not {@link TripStatus#BRIEF_COMPLETE} — the C2 unlock rule (USE-CASES §C2)
     */
    public static TripStatus requireStart(TripStatus from) {
        if (from != TripStatus.BRIEF_COMPLETE) {
            throw ValidationFailedException.field("status",
                    "research can start only from BRIEF_COMPLETE, not " + from);
        }
        return TripStatus.RESEARCH_QUEUED;
    }

    /** True when the trip is queued and a worker may move it to running. */
    public static boolean canBeginRunning(TripStatus from) {
        return from == TripStatus.RESEARCH_QUEUED;
    }

    /** True when a completed run may move the trip to ready. */
    public static boolean canBecomeReady(TripStatus from) {
        return from == TripStatus.RESEARCH_RUNNING;
    }

    /**
     * Destination selection (UC-C2-06). Only from {@link TripStatus#RESEARCH_READY}.
     *
     * @return {@link TripStatus#DESTINATION_SELECTED}
     * @throws ValidationFailedException when the trip is not ready for selection
     */
    public static TripStatus requireSelect(TripStatus from) {
        if (from != TripStatus.RESEARCH_READY) {
            throw ValidationFailedException.field("status",
                    "a destination can be selected only from RESEARCH_READY, not " + from);
        }
        return TripStatus.DESTINATION_SELECTED;
    }

    /**
     * Whether ranked recommendations may be listed for this trip status (UC-C2-03).
     *
     * <p>{@code DESTINATION_SELECTED} is allowed so the selected card stays readable after confirm.
     */
    public static boolean canListRecommendations(TripStatus from) {
        return from == TripStatus.RESEARCH_READY || from == TripStatus.DESTINATION_SELECTED;
    }

    /** True when a failed or cancelled run may return the trip to a re-runnable state. */
    public static boolean canRecover(TripStatus from) {
        return from == TripStatus.RESEARCH_QUEUED || from == TripStatus.RESEARCH_RUNNING;
    }

    /**
     * The failure-recovery target.
     *
     * @return {@link TripStatus#BRIEF_COMPLETE}
     * @throws ValidationFailedException when the trip is not in a research phase — a caller trying to
     *         "recover" a trip that already moved on is acting on a stale view
     */
    public static TripStatus recover(TripStatus from) {
        if (!canRecover(from)) {
            throw ValidationFailedException.field("status",
                    "only a queued or running trip can be recovered, not " + from);
        }
        return TripStatus.BRIEF_COMPLETE;
    }
}
