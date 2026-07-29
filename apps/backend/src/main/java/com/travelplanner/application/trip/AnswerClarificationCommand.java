package com.travelplanner.application.trip;

import com.travelplanner.domain.model.ClarificationAnswer;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/v1/trips/{tripId}/brief/actions/answer-clarification} (ADR 008 §3).
 *
 * <p>A typed diff, not a whole-body {@code PUT}. The no-{@code PATCH} rule stands, but re-sending
 * the entire brief to answer one question means the answer carries eight other fields the client
 * read some time ago — every one of them a chance to overwrite something the agent changed in the
 * meantime, from a form the user never touched.
 */
public record AnswerClarificationCommand(
        UUID tripId,
        int expectedVersion,
        List<ClarificationAnswer> answers) {
}
