package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.model.ClarificationAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * One answer to one clarification question — the typed diff ADR 008 §3 requires of an action body.
 *
 * <p>One optional slot per {@code ClarificationType}, exactly one of which must be filled. The
 * alternative, a single {@code value} string parsed according to the question's type, cannot carry
 * a budget (an amount plus a currency) or a date range (two dates) without inventing a text
 * encoding — and a text encoding is a parser that fails at runtime in a place the contract cannot
 * describe. Separate slots put the shape in OpenAPI and through codegen into the typed client,
 * which is the same argument ADR 008 §2 makes for carrying the version in the body.
 *
 * <p>"Exactly one" is enforced by {@link ClarificationAnswer} rather than by an annotation, because
 * it is a rule about the value and must hold for the LLM tool path task 19 adds, which Bean
 * Validation never sees.
 */
public record ClarificationAnswerPayload(
        @NotBlank String questionId,
        String text,
        Integer number,
        @Valid MoneyPayload money,
        @Valid DateRangePayload dateRange,
        String choice,
        List<String> choices) {

    /** @throws com.travelplanner.domain.exception.ValidationFailedException unless exactly one slot is filled */
    public ClarificationAnswer toAnswer() {
        return new ClarificationAnswer(
                questionId,
                text,
                number,
                MoneyPayload.toMoney(money),
                DateRangePayload.toDateRange(dateRange),
                choice,
                choices);
    }
}
