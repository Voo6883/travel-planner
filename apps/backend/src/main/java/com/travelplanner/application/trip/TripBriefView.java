package com.travelplanner.application.trip;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.TripBrief;

/**
 * Everything the intake surface needs about a brief, read or written, in one value.
 *
 * <p>The three parts travel together because they are only consistent together. A client that
 * fetched the brief and the trip status separately would, between the two calls, see a
 * {@code BRIEF_COMPLETE} trip beside a brief the agent had just re-opened — and would then render
 * a "start research" button that the server refuses. Returning them from one read makes that
 * window impossible.
 *
 * @param clarification always present, and empty exactly when the brief is complete. Never null, so
 *        a caller never has to distinguish "no questions" from "questions not computed"
 */
public record TripBriefView(TripBrief brief, TripStatus status, ClarificationNeeded clarification) {
}
