package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ClarificationType;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.PartySize;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The typed gap between a brief and a complete one (UC-C1-04, PLAN §3.1, §4.1.3).
 *
 * <p><strong>Derived, never stored.</strong> The questions are a pure function of the brief's
 * current fields, so {@link #forDetails} is both the completeness rule and the question list — one
 * computation read two ways. A persisted question table would be a second copy of that rule, and
 * the two would disagree the first time the agent wrote a field through
 * {@code update_trip_brief} without going through the endpoint that refreshes the table. It also
 * means a client never sees a question it has already answered, which is exactly the "form and
 * server state stay consistent" property task 18 is judged on.
 *
 * <p>{@link #isSatisfied()} is therefore the definition of {@code BRIEF_COMPLETE}, and C2 is
 * blocked until it holds (PLAN §3.1).
 *
 * <p>What is <em>not</em> asked matters as much as what is. Destination preference is absent from
 * the list: an empty preference is a legitimate answer that lets C2 rank the whole covered set, and
 * demanding one would make the product unusable for the traveller whose actual question is "where
 * should I go?".
 *
 * @param questions in a stable order, so a re-render after an answer does not reshuffle the form
 */
public record ClarificationNeeded(List<ClarificationQuestion> questions) {

    /** UC-C1-04 spells these ids; they are the wire contract and a rename breaks answers in flight. */
    public static final String QUESTION_TRAVEL_DATES = "travel_dates";
    public static final String QUESTION_DATE_FLEXIBILITY = "date_flexibility";
    public static final String QUESTION_DEPARTURE_CITY = "departure_city";
    public static final String QUESTION_BUDGET_MAX = "budget_max";
    public static final String QUESTION_PARTY_SIZE = "party_size";
    public static final String QUESTION_INTERESTS = "interests";
    public static final String QUESTION_PACE = "pace";

    /** The i18n namespace every prompt key sits under (PLAN §11: one namespace per feature). */
    private static final String PROMPT_NAMESPACE = "trip_brief.clarify_";

    public ClarificationNeeded {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    /** No questions — the brief is complete. */
    public static ClarificationNeeded none() {
        return new ClarificationNeeded(List.of());
    }

    /**
     * Every field the brief still needs, in form order.
     *
     * <p>Each condition tests for absence only. A field that is present is a field the value object
     * already validated, so there is nothing left here to disagree with — a brief cannot hold a
     * reversed date range or a negative budget for this method to complain about.
     */
    public static ClarificationNeeded forDetails(TripBriefDetails details) {
        if (details == null) {
            throw new IllegalArgumentException("details");
        }
        List<ClarificationQuestion> missing = new ArrayList<>();
        if (details.dates() == null) {
            missing.add(ClarificationQuestion.of(QUESTION_TRAVEL_DATES,
                    promptKey("dates"), ClarificationType.DATE_RANGE));
        }
        if (details.dateFlexibility() == null) {
            missing.add(ClarificationQuestion.ofChoices(QUESTION_DATE_FLEXIBILITY,
                    promptKey("date_flexibility"), ClarificationType.CHOICE, DateFlexibility.class));
        }
        if (details.departureCity() == null) {
            missing.add(ClarificationQuestion.of(QUESTION_DEPARTURE_CITY,
                    promptKey("departure"), ClarificationType.TEXT));
        }
        if (details.budget() == null) {
            missing.add(ClarificationQuestion.of(QUESTION_BUDGET_MAX,
                    promptKey("budget"), ClarificationType.MONEY));
        }
        if (details.party() == null) {
            missing.add(ClarificationQuestion.of(QUESTION_PARTY_SIZE,
                    promptKey("party"), ClarificationType.NUMBER));
        }
        if (details.interests().isEmpty()) {
            missing.add(ClarificationQuestion.ofChoices(QUESTION_INTERESTS,
                    promptKey("interests"), ClarificationType.MULTI_CHOICE, TravelInterest.class));
        }
        if (details.pace() == null) {
            missing.add(ClarificationQuestion.ofChoices(QUESTION_PACE,
                    promptKey("pace"), ClarificationType.CHOICE, TravelPace.class));
        }
        return new ClarificationNeeded(missing);
    }

    /** True when nothing is outstanding — the definition of {@code BRIEF_COMPLETE}. */
    public boolean isSatisfied() {
        return questions.isEmpty();
    }

    /**
     * Applies answers to the fields their questions were asked about, and returns the revised
     * details for re-validation.
     *
     * <p>Three things are refused rather than ignored, because a silently dropped answer is
     * indistinguishable to the user from one the server accepted and then lost:
     *
     * <ul>
     *   <li>an answer to a question that is not outstanding — it was already answered, or never
     *       asked, and applying it would let a client overwrite an arbitrary field through the
     *       clarification endpoint instead of through {@code PUT .../brief};</li>
     *   <li>an answer whose value type does not match the question's;</li>
     *   <li>two answers to the same question, where "last one wins" would be a coin toss.</li>
     * </ul>
     */
    public TripBriefDetails applyAnswers(TripBriefDetails details,
            List<ClarificationAnswer> answers) {
        if (details == null || answers == null) {
            throw new IllegalArgumentException("details and answers are both required");
        }
        Map<String, ClarificationQuestion> outstanding = new LinkedHashMap<>();
        questions.forEach(question -> outstanding.put(question.id(), question));

        TripBriefDetails revised = details;
        for (ClarificationAnswer answer : answers) {
            ClarificationQuestion question = outstanding.remove(answer.questionId());
            if (question == null) {
                throw ValidationFailedException.field("answers",
                        "'" + answer.questionId() + "' is not an outstanding question");
            }
            if (question.type() != answer.type()) {
                throw ValidationFailedException.field("answers",
                        "'" + answer.questionId() + "' expects a " + question.type() + " answer");
            }
            revised = apply(revised, answer);
        }
        return revised;
    }

    private static TripBriefDetails apply(TripBriefDetails details, ClarificationAnswer answer) {
        return switch (answer.questionId()) {
            case QUESTION_TRAVEL_DATES -> details.withDates(answer.dateRange());
            case QUESTION_DATE_FLEXIBILITY -> details.withDateFlexibility(
                    constantOf(DateFlexibility.class, answer.choice(), QUESTION_DATE_FLEXIBILITY));
            case QUESTION_DEPARTURE_CITY -> details.withDepartureCity(answer.text());
            case QUESTION_BUDGET_MAX -> details.withBudget(answer.money());
            case QUESTION_PARTY_SIZE -> details.withParty(PartySize.ofAdults(answer.number()));
            case QUESTION_INTERESTS -> details.withInterests(answer.choices().stream()
                    .map(value -> constantOf(TravelInterest.class, value, QUESTION_INTERESTS))
                    .toList());
            case QUESTION_PACE -> details.withPace(
                    constantOf(TravelPace.class, answer.choice(), QUESTION_PACE));
            default -> throw ValidationFailedException.field("answers",
                    "'" + answer.questionId() + "' has no field to set");
        };
    }

    /**
     * Resolves a choice against its vocabulary. {@code Enum.valueOf} would throw
     * {@link IllegalArgumentException}, which the handler renders as {@code 500 internal_error} —
     * a client typo must not look like a server fault.
     */
    private static <E extends Enum<E>> E constantOf(Class<E> vocabulary, String value,
            String questionId) {
        try {
            return Enum.valueOf(vocabulary, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw ValidationFailedException.field("answers",
                    "'" + value + "' is not an option for '" + questionId + "'");
        }
    }

    private static String promptKey(String suffix) {
        return PROMPT_NAMESPACE + suffix;
    }
}
