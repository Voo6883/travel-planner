package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ClarificationType;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import java.util.List;
import java.util.Objects;

/**
 * One answer to one {@link ClarificationQuestion} — a typed diff of exactly one brief field
 * (ADR 008 §3).
 *
 * <p><strong>One slot per {@link ClarificationType}, and exactly one of them filled.</strong> The
 * alternative — a single {@code String value} parsed according to the question's type — cannot
 * carry a {@link Money} (an amount and a currency) or a {@link DateRange} (two dates) without
 * inventing a text encoding, and a text encoding is a parser that fails at runtime in a place the
 * contract cannot describe. Separate slots put the shape in OpenAPI, through codegen, and into the
 * typed client, which is the same argument ADR 008 §2 makes for carrying the version in the body.
 *
 * <p>The compact constructor enforces "exactly one", so an answer carrying two values or none can
 * never reach the routing switch in {@link ClarificationNeeded#applyAnswers}. Whether the slot that
 * <em>is</em> filled matches the question that was asked is checked there, because only that class
 * knows which question the id refers to.
 *
 * @param questionId the {@link ClarificationQuestion#id()} being answered
 */
public record ClarificationAnswer(
        String questionId,
        String text,
        Integer number,
        Money money,
        DateRange dateRange,
        String choice,
        List<String> choices) {

    public ClarificationAnswer {
        Objects.requireNonNull(questionId, "questionId");
        choices = choices == null ? null : List.copyOf(choices);
        int filled = count(text) + count(number) + count(money) + count(dateRange)
                + count(choice) + count(choices);
        if (filled != 1) {
            throw ValidationFailedException.field("answers",
                    "answer to '" + questionId + "' must carry exactly one value");
        }
    }

    public static ClarificationAnswer ofText(String questionId, String text) {
        return new ClarificationAnswer(questionId, text, null, null, null, null, null);
    }

    public static ClarificationAnswer ofNumber(String questionId, Integer number) {
        return new ClarificationAnswer(questionId, null, number, null, null, null, null);
    }

    public static ClarificationAnswer ofMoney(String questionId, Money money) {
        return new ClarificationAnswer(questionId, null, null, money, null, null, null);
    }

    public static ClarificationAnswer ofDateRange(String questionId, DateRange dateRange) {
        return new ClarificationAnswer(questionId, null, null, null, dateRange, null, null);
    }

    public static ClarificationAnswer ofChoice(String questionId, String choice) {
        return new ClarificationAnswer(questionId, null, null, null, null, choice, null);
    }

    public static ClarificationAnswer ofChoices(String questionId, List<String> choices) {
        return new ClarificationAnswer(questionId, null, null, null, null, null, choices);
    }

    /** Which slot this answer filled, so the question's type can be compared against it. */
    public ClarificationType type() {
        if (text != null) {
            return ClarificationType.TEXT;
        }
        if (number != null) {
            return ClarificationType.NUMBER;
        }
        if (money != null) {
            return ClarificationType.MONEY;
        }
        if (dateRange != null) {
            return ClarificationType.DATE_RANGE;
        }
        if (choice != null) {
            return ClarificationType.CHOICE;
        }
        return ClarificationType.MULTI_CHOICE;
    }

    /**
     * An empty {@code choices} list counts as unfilled. A multi-choice answer that selects nothing
     * is not an answer — it is the question, restated.
     */
    private static int count(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof List<?> values && values.isEmpty()) {
            return 0;
        }
        return 1;
    }
}
