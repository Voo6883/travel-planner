package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ClarificationType;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One typed question the brief cannot be completed without (UC-C1-04, PLAN §4.1.3).
 *
 * <p><strong>{@code promptKey}, never a sentence.</strong> The question text is a translation key
 * ({@code trip_brief.clarify_budget}) resolved by the frontend against
 * {@code locales/<lang>/trip_brief.json}. An English string here would be untranslatable and would
 * put user-facing copy in the domain, where the Malay build could never reach it (PLAN §11).
 *
 * @param id stable, {@code snake_case}, and also the name of the brief field the answer sets. It is
 *        what {@link ClarificationNeeded#applyAnswers} routes on, so renaming one invalidates any
 *        answer a client had in flight
 * @param options the permitted values for {@link ClarificationType#CHOICE} and
 *        {@link ClarificationType#MULTI_CHOICE}, and empty for every other type. Present in the
 *        question rather than fetched separately so a client can render the control from one
 *        payload
 * @param required whether the brief can complete without an answer. Every question task 18
 *        generates is required, because it only ever asks about a field completeness depends on;
 *        the flag exists for the LLM-authored questions task 19 adds, which can be optional
 */
public record ClarificationQuestion(
        String id,
        String promptKey,
        ClarificationType type,
        List<String> options,
        boolean required) {

    public ClarificationQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(promptKey, "promptKey");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
        if (expectsOptions(type) == options.isEmpty()) {
            // A choice with nothing to choose from cannot be answered; options on a free-text
            // question are a control the client would render and the server would then ignore.
            throw ValidationFailedException.field("options",
                    "are required for CHOICE and MULTI_CHOICE and forbidden for every other type");
        }
    }

    /** A question with no options — {@code TEXT}, {@code NUMBER}, {@code MONEY}, {@code DATE_RANGE}. */
    public static ClarificationQuestion of(String id, String promptKey, ClarificationType type) {
        return new ClarificationQuestion(id, promptKey, type, List.of(), true);
    }

    /** A {@code CHOICE} or {@code MULTI_CHOICE} question over the constants of an enum. */
    public static ClarificationQuestion ofChoices(String id, String promptKey,
            ClarificationType type, Class<? extends Enum<?>> vocabulary) {
        List<String> options = Arrays.stream(vocabulary.getEnumConstants())
                .map(Enum::name)
                .toList();
        return new ClarificationQuestion(id, promptKey, type, options, true);
    }

    private static boolean expectsOptions(ClarificationType type) {
        return type == ClarificationType.CHOICE || type == ClarificationType.MULTI_CHOICE;
    }
}
