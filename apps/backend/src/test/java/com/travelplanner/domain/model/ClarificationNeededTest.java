package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ClarificationType;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The clarification rule (UC-C1-04, PLAN §4.1.3) — both halves of it, because
 * {@code forDetails} is simultaneously the question list and the definition of
 * {@code BRIEF_COMPLETE}.
 */
class ClarificationNeededTest {

    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));

    private static TripBriefDetails complete() {
        return TripBriefDetails.empty()
                .withDates(SPRING)
                .withDateFlexibility(DateFlexibility.FLEXIBLE_WEEK)
                .withDepartureCity("Kuala Lumpur")
                .withBudget(Money.of("4000.00", "MYR"))
                .withParty(new PartySize(2, 0))
                .withInterests(List.of(TravelInterest.FOOD))
                .withPace(TravelPace.MODERATE);
    }

    @Test
    void anEmptyBriefIsAskedAboutEveryRequiredFieldInFormOrder() {
        ClarificationNeeded needed = ClarificationNeeded.forDetails(TripBriefDetails.empty());

        assertThat(needed.questions()).extracting(ClarificationQuestion::id).containsExactly(
                ClarificationNeeded.QUESTION_TRAVEL_DATES,
                ClarificationNeeded.QUESTION_DATE_FLEXIBILITY,
                ClarificationNeeded.QUESTION_DEPARTURE_CITY,
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_PARTY_SIZE,
                ClarificationNeeded.QUESTION_INTERESTS,
                ClarificationNeeded.QUESTION_PACE);
        assertThat(needed.isSatisfied()).isFalse();
    }

    @Test
    void destinationPreferenceIsNeverAskedAbout() {
        // An empty preference is a legitimate answer that lets C2 rank the whole covered set.
        // Demanding one would make the product unusable for "where should I go?".
        assertThat(ClarificationNeeded.forDetails(TripBriefDetails.empty()).questions())
                .extracting(ClarificationQuestion::id)
                .doesNotContain("destinations");
        assertThat(ClarificationNeeded.forDetails(complete()).isSatisfied()).isTrue();
    }

    @Test
    void aCompleteBriefHasNoQuestionsLeft() {
        ClarificationNeeded needed = ClarificationNeeded.forDetails(complete());

        assertThat(needed.questions()).isEmpty();
        assertThat(needed.isSatisfied()).isTrue();
        assertThat(ClarificationNeeded.none().isSatisfied()).isTrue();
    }

    @Test
    void removingOneFieldReopensExactlyThatQuestion() {
        // The property C1 lives on: an edit that un-completes the brief must un-complete the
        // status too, and the question that comes back has to be the one the edit removed.
        ClarificationNeeded needed =
                ClarificationNeeded.forDetails(complete().withBudget(null));

        assertThat(needed.questions()).singleElement()
                .satisfies(question -> {
                    assertThat(question.id()).isEqualTo(ClarificationNeeded.QUESTION_BUDGET_MAX);
                    assertThat(question.promptKey()).isEqualTo("trip_brief.clarify_budget");
                    assertThat(question.type()).isEqualTo(ClarificationType.MONEY);
                    assertThat(question.options()).isEmpty();
                    assertThat(question.required()).isTrue();
                });
    }

    @Test
    void choiceQuestionsCarryTheirWholeVocabularyInline() {
        // A client renders the control from one payload; a second call to fetch options would be
        // a round trip for a list that cannot change between them.
        ClarificationNeeded needed = ClarificationNeeded.forDetails(TripBriefDetails.empty());

        assertThat(questionOf(needed, ClarificationNeeded.QUESTION_DATE_FLEXIBILITY).options())
                .containsExactly("FIXED", "FLEXIBLE_WEEK", "FLEXIBLE_MONTH");
        assertThat(questionOf(needed, ClarificationNeeded.QUESTION_INTERESTS).type())
                .isEqualTo(ClarificationType.MULTI_CHOICE);
        assertThat(questionOf(needed, ClarificationNeeded.QUESTION_PACE).options())
                .containsExactly("RELAXED", "MODERATE", "PACKED");
    }

    @Test
    void answeringEveryOutstandingQuestionCompletesTheBrief() {
        TripBriefDetails empty = TripBriefDetails.empty();

        TripBriefDetails answered = ClarificationNeeded.forDetails(empty).applyAnswers(empty, List.of(
                ClarificationAnswer.ofDateRange(ClarificationNeeded.QUESTION_TRAVEL_DATES, SPRING),
                ClarificationAnswer.ofChoice(ClarificationNeeded.QUESTION_DATE_FLEXIBILITY, "FIXED"),
                ClarificationAnswer.ofText(ClarificationNeeded.QUESTION_DEPARTURE_CITY, "Penang"),
                ClarificationAnswer.ofMoney(ClarificationNeeded.QUESTION_BUDGET_MAX,
                        Money.of("2500.00", "MYR")),
                ClarificationAnswer.ofNumber(ClarificationNeeded.QUESTION_PARTY_SIZE, 3),
                ClarificationAnswer.ofChoices(ClarificationNeeded.QUESTION_INTERESTS,
                        List.of("food", "NATURE")),
                ClarificationAnswer.ofChoice(ClarificationNeeded.QUESTION_PACE, "packed")));

        assertThat(answered.dates()).isEqualTo(SPRING);
        assertThat(answered.dateFlexibility()).isEqualTo(DateFlexibility.FIXED);
        assertThat(answered.departureCity()).isEqualTo("Penang");
        assertThat(answered.budget()).isEqualTo(Money.of("2500", "MYR"));
        assertThat(answered.party()).isEqualTo(PartySize.ofAdults(3));
        assertThat(answered.interests())
                .containsExactly(TravelInterest.FOOD, TravelInterest.NATURE);
        assertThat(answered.pace()).isEqualTo(TravelPace.PACKED);
        assertThat(ClarificationNeeded.forDetails(answered).isSatisfied()).isTrue();
    }

    @Test
    void answeringSomeQuestionsLeavesTheRestOutstanding() {
        TripBriefDetails empty = TripBriefDetails.empty();

        TripBriefDetails partial = ClarificationNeeded.forDetails(empty).applyAnswers(empty,
                List.of(ClarificationAnswer.ofDateRange(
                        ClarificationNeeded.QUESTION_TRAVEL_DATES, SPRING)));

        assertThat(ClarificationNeeded.forDetails(partial).questions())
                .extracting(ClarificationQuestion::id)
                .doesNotContain(ClarificationNeeded.QUESTION_TRAVEL_DATES)
                .contains(ClarificationNeeded.QUESTION_BUDGET_MAX);
    }

    @Test
    void anAnswerToAQuestionThatIsNotOutstandingIsRefused() {
        // Otherwise the clarification action becomes a second, unvalidated way to write any brief
        // field — one that skips the coverage check the whole-body save performs.
        TripBriefDetails details = complete();

        assertThatThrownBy(() -> ClarificationNeeded.forDetails(details).applyAnswers(details,
                List.of(ClarificationAnswer.ofMoney(ClarificationNeeded.QUESTION_BUDGET_MAX,
                        Money.of("1.00", "MYR")))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void twoAnswersToTheSameQuestionAreRefusedRatherThanSilentlyResolved() {
        // "Last one wins" would be a coin toss the user cannot see.
        TripBriefDetails empty = TripBriefDetails.empty();
        ClarificationNeeded needed = ClarificationNeeded.forDetails(empty);
        List<ClarificationAnswer> twice = List.of(
                ClarificationAnswer.ofText(ClarificationNeeded.QUESTION_DEPARTURE_CITY, "Penang"),
                ClarificationAnswer.ofText(ClarificationNeeded.QUESTION_DEPARTURE_CITY, "Ipoh"));

        assertThatThrownBy(() -> needed.applyAnswers(empty, twice))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void anAnswerOfTheWrongValueTypeIsRefused() {
        TripBriefDetails empty = TripBriefDetails.empty();
        ClarificationNeeded needed = ClarificationNeeded.forDetails(empty);
        List<ClarificationAnswer> wrongType = List.of(
                ClarificationAnswer.ofText(ClarificationNeeded.QUESTION_BUDGET_MAX, "4000 MYR"));

        assertThatThrownBy(() -> needed.applyAnswers(empty, wrongType))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    void aChoiceOutsideTheVocabularyIsAClientErrorAndNotAServerFault() {
        // Enum.valueOf would throw IllegalArgumentException, which the handler renders as
        // 500 internal_error — a typo must not look like a bug in this service.
        TripBriefDetails empty = TripBriefDetails.empty();
        ClarificationNeeded needed = ClarificationNeeded.forDetails(empty);
        List<ClarificationAnswer> unknown = List.of(
                ClarificationAnswer.ofChoice(ClarificationNeeded.QUESTION_PACE, "LEISURELY"));

        assertThatThrownBy(() -> needed.applyAnswers(empty, unknown))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void anAnswerCarryingNoValueOrTwoIsRefusedBeforeItReachesRouting() {
        assertThatThrownBy(() -> new ClarificationAnswer("pace", null, null, null, null, null, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> new ClarificationAnswer("pace", "RELAXED", 2, null, null, null, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> ClarificationAnswer.ofChoices("interests", List.of()))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void everyAnswerSlotReportsTheTypeItStandsFor() {
        assertThat(ClarificationAnswer.ofText("a", "x").type()).isEqualTo(ClarificationType.TEXT);
        assertThat(ClarificationAnswer.ofNumber("a", 1).type()).isEqualTo(ClarificationType.NUMBER);
        assertThat(ClarificationAnswer.ofMoney("a", Money.of("1", "MYR")).type())
                .isEqualTo(ClarificationType.MONEY);
        assertThat(ClarificationAnswer.ofDateRange("a", SPRING).type())
                .isEqualTo(ClarificationType.DATE_RANGE);
        assertThat(ClarificationAnswer.ofChoice("a", "x").type())
                .isEqualTo(ClarificationType.CHOICE);
        assertThat(ClarificationAnswer.ofChoices("a", List.of("x")).type())
                .isEqualTo(ClarificationType.MULTI_CHOICE);
    }

    @Test
    void aChoiceQuestionWithNothingToChooseFromCannotBeBuilt() {
        assertThatThrownBy(() -> new ClarificationQuestion("pace", "trip_brief.clarify_pace",
                ClarificationType.CHOICE, List.of(), true))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> new ClarificationQuestion("departure_city",
                "trip_brief.clarify_departure", ClarificationType.TEXT, List.of("a"), true))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void applyingAnswersToNothingIsARefusalRatherThanASilentNoOp() {
        ClarificationNeeded needed = ClarificationNeeded.forDetails(TripBriefDetails.empty());

        assertThatThrownBy(() -> needed.applyAnswers(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> needed.applyAnswers(TripBriefDetails.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClarificationNeeded.forDetails(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ClarificationQuestion questionOf(ClarificationNeeded needed, String id) {
        return needed.questions().stream()
                .filter(question -> question.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
