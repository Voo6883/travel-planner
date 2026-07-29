package com.travelplanner.api.dto.trip;

import com.travelplanner.api.dto.VersionedMutation;
import com.travelplanner.domain.model.ClarificationAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code POST /api/v1/trips/{tripId}/brief/actions/answer-clarification} (ADR 008 §3).
 *
 * <p>A typed {@code POST} action rather than a whole-body {@code PUT}. The no-{@code PATCH} rule
 * stands, but re-sending nine brief fields to answer one question means eight of them were read
 * some time ago — and each is a chance to overwrite something the agent changed since, from a form
 * control the user never touched.
 *
 * <p>{@code @NotEmpty}: an action that answers nothing is a request that would consume a version
 * and change no state, leaving the client to wonder which of the two happened.
 */
public record AnswerClarificationRequest(
        @NotNull @Min(0) Integer expectedVersion,
        @NotEmpty @Valid List<ClarificationAnswerPayload> answers) implements VersionedMutation {

    public List<ClarificationAnswer> toAnswers() {
        return answers.stream().map(ClarificationAnswerPayload::toAnswer).toList();
    }
}
