package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.enums.ClarificationType;
import com.travelplanner.domain.model.ClarificationQuestion;
import java.util.List;

/**
 * One outstanding question, as UC-C1-04 publishes it.
 *
 * <p>{@code prompt_key} is a translation key, never a sentence: the frontend resolves it against
 * {@code locales/<lang>/trip_brief.json}, so the same payload renders in English and Malay. An
 * English string here would be a user-facing message the Malay build could never reach (PLAN §11).
 */
public record ClarificationQuestionResponse(
        String id,
        String promptKey,
        ClarificationType type,
        List<String> options,
        boolean required) {

    public static ClarificationQuestionResponse from(ClarificationQuestion question) {
        return new ClarificationQuestionResponse(question.id(), question.promptKey(),
                question.type(), question.options(), question.required());
    }
}
