package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which status moves the intake slice is allowed to make.
 *
 * <p>{@link TripStatus} carries the whole wizard vocabulary and deliberately holds no transition
 * logic, because the tasks that own the later states have not been written yet and a guessed table
 * would lock decisions nobody reviewed. This class is the part task 18 <em>can</em> state: the
 * three statuses C1 owns, and the archive move.
 *
 * <p><strong>Everything else is refused, not ignored.</strong> A request asking to move a trip to
 * {@code RESEARCH_READY} is not a research start — it is a client writing a state no code in this
 * slice can honour, and accepting it would let a caller skip the C2 gate PLAN §3.1 depends on by
 * setting the field directly. The refusal is {@code validation_failed} on {@code status}, so the
 * caller learns which value it sent was the problem.
 *
 * <p>Adding a state here is a task's explicit decision. The successor of {@code BRIEF_COMPLETE} is
 * {@code RESEARCH_QUEUED} and it belongs to task 22, which will widen this set together with the
 * endpoint that performs the move.
 */
public final class TripStatusTransition {

    /**
     * The statuses C1 owns. All six moves between them are legal, including
     * {@code BRIEF_COMPLETE → CLARIFICATION_NEEDED}: an edit that removes a field un-completes the
     * brief, and refusing that move would leave the trip claiming a completeness it no longer has.
     */
    public static final Set<TripStatus> INTAKE_STATUSES = EnumSet.of(
            TripStatus.DRAFT, TripStatus.CLARIFICATION_NEEDED, TripStatus.BRIEF_COMPLETE);

    private TripStatusTransition() {
    }

    /** True when the status is one the intake slice may read or write. */
    public static boolean isIntakeStatus(TripStatus status) {
        return status != null && INTAKE_STATUSES.contains(status);
    }

    /**
     * Guards a move between two intake statuses.
     *
     * @throws ValidationFailedException when either end is outside {@link #INTAKE_STATUSES}. That
     *         covers both directions on purpose: a trip already in C2 must not be dragged back into
     *         intake, and an intake trip must not be pushed forward without the task that owns the
     *         work behind the new state
     */
    public static void require(TripStatus from, TripStatus to) {
        if (!isIntakeStatus(from)) {
            throw ValidationFailedException.field("status",
                    "a trip in " + from + " is no longer editable through the brief");
        }
        if (!isIntakeStatus(to)) {
            throw ValidationFailedException.field("status",
                    to + " is not a status the brief may set");
        }
    }
}
