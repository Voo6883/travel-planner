package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.model.ClarificationNeeded;
import java.util.List;

/**
 * The typed gap between the brief and a complete one (UC-C1-04, PLAN §4.1.3).
 *
 * <p>Always present on a brief response, and empty exactly when the brief is complete — so a client
 * never has to tell "no questions" apart from "questions not computed", and the render is one
 * branch on an array length rather than a null check.
 *
 * <p>An object around the array rather than the array itself, for the reason every other wrapper
 * here exists: a top-level array cannot gain a field, and this one will want a "why we are asking"
 * hint once task 19 lets the model author questions of its own.
 */
public record ClarificationResponse(List<ClarificationQuestionResponse> questions) {

    public static ClarificationResponse from(ClarificationNeeded clarification) {
        return new ClarificationResponse(clarification.questions().stream()
                .map(ClarificationQuestionResponse::from)
                .toList());
    }
}
