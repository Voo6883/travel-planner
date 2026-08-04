package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripBriefExtractionOutcome;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The one door between "the model said" and "the brief holds".
 *
 * <p>Every case here is a value a model can and does produce, put through the same value objects a
 * form submission goes through. What is being proved is that extraction is not a privileged write
 * path: there is no input to {@link TripBriefExtraction#from} that puts something into a
 * {@link TripBriefDetails} which {@code PUT .../brief} would have rejected.
 */
class TripBriefExtractionTest {

    private static final String VERSION = "trip-brief-extract@v1";

    // -------------------------------------------------------------------------------------
    // The accepting path
    // -------------------------------------------------------------------------------------

    @Test
    void aCleanDraftBecomesACompleteBrief() {
        TripBriefExtraction extraction = TripBriefExtraction.from(complete(), null, VERSION);

        assertThat(extraction.outcome()).isEqualTo(TripBriefExtractionOutcome.EXTRACTED);
        assertThat(extraction.isComplete()).isTrue();
        assertThat(extraction.unresolvedFields()).isEmpty();
        assertThat(extraction.promptVersion()).isEqualTo(VERSION);
        assertThat(extraction.failureCode()).isNull();
        assertThat(extraction.details().destinations()).containsExactly("penang");
        assertThat(extraction.details().dates())
                .isEqualTo(DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12)));
        assertThat(extraction.details().budget()).isEqualTo(Money.of("4000.00", "MYR"));
        assertThat(extraction.details().party()).isEqualTo(new PartySize(2, 1));
        assertThat(extraction.details().interests())
                .containsExactly(TravelInterest.FOOD, TravelInterest.NATURE);
        assertThat(extraction.details().pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(extraction.details().dateFlexibility()).isEqualTo(DateFlexibility.FIXED);
    }

    @Test
    void aFieldTheModelSaidNothingAboutKeepsWhatTheBriefAlreadyHeld() {
        TripBriefDetails known = TripBriefDetails.empty()
                .withBudget(Money.of("900.00", "MYR"))
                .withDepartureCity("Ipoh");

        TripBriefExtraction extraction = TripBriefExtraction.from(
                draft().withPace("PACKED"), known, VERSION);

        assertThat(extraction.details().pace()).isEqualTo(TravelPace.PACKED);
        assertThat(extraction.details().budget()).isEqualTo(Money.of("900.00", "MYR"));
        assertThat(extraction.details().departureCity()).isEqualTo("Ipoh");
    }

    @Test
    void slugsAreNormalisedByTheSameRuleTheFormUses() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                draft().withDestinations(List.of("Penang", "penang ", "")), null, VERSION);

        assertThat(extraction.details().destinations()).containsExactly("penang");
    }

    // -------------------------------------------------------------------------------------
    // Domain invariants are authoritative
    // -------------------------------------------------------------------------------------

    @Test
    void aNegativeBudgetIsRefusedByMoneyAndBecomesAQuestion() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withBudget("-5", "MYR"), null, VERSION);

        assertThat(extraction.details().budget()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_BUDGET_MAX);
        assertThat(questionIds(extraction)).contains(ClarificationNeeded.QUESTION_BUDGET_MAX);
    }

    @Test
    void aBudgetWithNoCurrencyIsNotMoneyAndIsLeftAbsent() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withBudget("4000.00", null), null, VERSION);

        assertThat(extraction.details().budget()).isNull();
        // Not a refusal: the model said nothing usable, which is the ordinary "not answered" case.
        assertThat(extraction.unresolvedFields()).isEmpty();
    }

    @Test
    void anUnknownCurrencyIsRefused() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withBudget("4000.00", "XYZ"), null, VERSION);

        assertThat(extraction.details().budget()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_BUDGET_MAX);
    }

    @Test
    void aPartyLargerThanAnySupplierQuotesIsRefusedByPartySize() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withParty(30, 0), null, VERSION);

        assertThat(extraction.details().party()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_PARTY_SIZE);
    }

    @Test
    void childrenWithNoAdultIsRefusedRatherThanSilentlyMadeIntoOneAdult() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withParty(null, 2), null, VERSION);

        assertThat(extraction.details().party()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_PARTY_SIZE);
    }

    @Test
    void aReversedDateRangeIsRefusedByDateRange() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withDates("2026-04-12", "2026-04-03"), null, VERSION);

        assertThat(extraction.details().dates()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_TRAVEL_DATES);
    }

    @Test
    void aDateThatIsAPhraseRatherThanADateIsRefused() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withDates("next spring", "2026-04-03"), null, VERSION);

        assertThat(extraction.details().dates()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_TRAVEL_DATES);
    }

    @Test
    void oneMissingEndOfARangeIsNotHalfAnAnswer() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withDates("2026-04-03", null), null, VERSION);

        assertThat(extraction.details().dates()).isNull();
        assertThat(extraction.unresolvedFields()).isEmpty();
    }

    @Test
    void aDepartureCityLongerThanTheColumnIsRefused() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withDepartureCity("x".repeat(200)), null, VERSION);

        assertThat(extraction.details().departureCity()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_DEPARTURE_CITY);
    }

    @Test
    void moreDestinationsThanAnybodyRankedAreRefusedAsOneField() {
        List<String> tooMany = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");

        TripBriefExtraction extraction = TripBriefExtraction.from(
                draft().withDestinations(tooMany), null, VERSION);

        assertThat(extraction.details().destinations()).isEmpty();
        assertThat(extraction.unresolvedFields())
                .containsExactly(TripBriefExtraction.FIELD_DESTINATIONS);
    }

    @Test
    void anInventedEnumConstantIsRefusedRatherThanCrashing() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withPace("BLISTERING").withFlexibility("WHENEVER"), null, VERSION);

        assertThat(extraction.details().pace()).isNull();
        assertThat(extraction.details().dateFlexibility()).isNull();
        assertThat(extraction.unresolvedFields()).containsExactlyInAnyOrder(
                ClarificationNeeded.QUESTION_PACE,
                ClarificationNeeded.QUESTION_DATE_FLEXIBILITY);
    }

    @Test
    void unknownInterestsAreDroppedIndividuallyButAListOfOnlyUnknownsIsRefused() {
        TripBriefExtraction partly = TripBriefExtraction.from(
                complete().withInterests(List.of("FOOD", "SPELUNKING")), null, VERSION);
        TripBriefExtraction none = TripBriefExtraction.from(
                complete().withInterests(List.of("SPELUNKING")), null, VERSION);

        assertThat(partly.details().interests()).containsExactly(TravelInterest.FOOD);
        assertThat(partly.unresolvedFields()).isEmpty();
        assertThat(none.details().interests()).isEmpty();
        assertThat(none.unresolvedFields()).containsExactly(ClarificationNeeded.QUESTION_INTERESTS);
    }

    @Test
    void duplicateInterestsCollapseRatherThanCountingTwice() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withInterests(List.of("FOOD", "food", " FOOD ")), null, VERSION);

        assertThat(extraction.details().interests()).containsExactly(TravelInterest.FOOD);
    }

    // -------------------------------------------------------------------------------------
    // Ambiguity — ask rather than guess
    // -------------------------------------------------------------------------------------

    @Test
    void aFieldTheModelFlaggedIsDiscardedEvenThoughItParses() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withAmbiguous(List.of("budget_max", "travel_dates")), null, VERSION);

        assertThat(extraction.details().budget()).isNull();
        assertThat(extraction.details().dates()).isNull();
        assertThat(extraction.unresolvedFields())
                .containsExactlyInAnyOrder("budget_max", "travel_dates");
        assertThat(extraction.isComplete()).isFalse();
    }

    @Test
    void aFlagIsCaseAndWhitespaceInsensitiveBecauseModelsAreNot() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withAmbiguous(Arrays.asList(" Budget_Max ", null, "not_a_field")),
                null, VERSION);

        assertThat(extraction.details().budget()).isNull();
        assertThat(extraction.unresolvedFields()).containsExactly("budget_max");
    }

    // -------------------------------------------------------------------------------------
    // Surprise me, coverage, fallback
    // -------------------------------------------------------------------------------------

    @Test
    void surpriseMeClearsAnyDestinationAndSurvivesOnTheResult() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withSurpriseMe(true), null, VERSION);

        assertThat(extraction.surpriseMe()).isTrue();
        assertThat(extraction.details().surpriseMe()).isTrue();
        assertThat(extraction.details().destinations()).isEmpty();
        assertThat(extraction.isComplete()).isTrue();
    }

    @Test
    void surpriseMeAlsoClearsADestinationTheBriefAlreadyHeld() {
        TripBriefDetails known = TripBriefDetails.empty().withDestinations(List.of("penang"));

        TripBriefExtraction extraction = TripBriefExtraction.from(
                draft().withSurpriseMe(true), known, VERSION);

        assertThat(extraction.details().surpriseMe()).isTrue();
        assertThat(extraction.details().destinations()).isEmpty();
    }

    @Test
    void removingAnUncoveredDestinationLeavesEveryOtherFieldIntact() {
        TripBriefExtraction extraction = TripBriefExtraction.from(
                complete().withDestinations(List.of("penang", "osaka")), null, VERSION);

        TripBriefExtraction pruned = extraction.withoutDestinations(List.of("osaka"));

        assertThat(pruned.details().destinations()).containsExactly("penang");
        assertThat(pruned.details().budget()).isEqualTo(Money.of("4000.00", "MYR"));
        assertThat(pruned.promptVersion()).isEqualTo(VERSION);
        assertThat(pruned.outcome()).isEqualTo(TripBriefExtractionOutcome.EXTRACTED);
    }

    @Test
    void removingNothingReturnsTheSameExtraction() {
        TripBriefExtraction extraction = TripBriefExtraction.from(complete(), null, VERSION);

        assertThat(extraction.withoutDestinations(List.of())).isSameAs(extraction);
        assertThat(extraction.withoutDestinations(null)).isSameAs(extraction);
    }

    @Test
    void theFallbackLeavesTheBriefAloneAndAsksForEverythingOutstanding() {
        TripBriefDetails known = TripBriefDetails.empty()
                .withDepartureCity("Ipoh")
                .withSurpriseMe(true);

        TripBriefExtraction extraction =
                TripBriefExtraction.fallback(known, "ai_timeout", VERSION);

        assertThat(extraction.outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(extraction.failureCode()).isEqualTo("ai_timeout");
        assertThat(extraction.surpriseMe()).isTrue();
        assertThat(extraction.details()).isEqualTo(known);
        assertThat(extraction.clarification().questions()).hasSize(6);
    }

    @Test
    void theFallbackForABrandNewBriefIsAnEmptyOne() {
        TripBriefExtraction extraction = TripBriefExtraction.fallback(null, "ai_timeout", VERSION);

        assertThat(extraction.details()).isEqualTo(TripBriefDetails.empty());
        assertThat(extraction.clarification().questions()).hasSize(7);
    }

    // -------------------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------------------

    @Test
    void theRecordRefusesToExistWithoutDetailsOrAnOutcome() {
        assertThatThrownBy(() -> new TripBriefExtraction(null, List.of(),
                TripBriefExtractionOutcome.EXTRACTED, null, VERSION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TripBriefExtraction(TripBriefDetails.empty(),
                List.of(), null, null, VERSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullListsAndVersionsAreNormalisedRatherThanLeftToBlowUpLater() {
        TripBriefExtraction extraction = new TripBriefExtraction(TripBriefDetails.empty(), null,
                TripBriefExtractionOutcome.EXTRACTED, null, null);

        assertThat(extraction.unresolvedFields()).isEmpty();
        assertThat(extraction.promptVersion()).isEmpty();
    }

    @Test
    void aNullDraftIsAProgrammingErrorRatherThanAnEmptyExtraction() {
        assertThatThrownBy(() -> TripBriefExtraction.from(null, null, VERSION))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private static List<String> questionIds(TripBriefExtraction extraction) {
        return extraction.clarification().questions().stream()
                .map(ClarificationQuestion::id)
                .toList();
    }

    private static Draft draft() {
        return new Draft(List.of(), false, null, null, null, null, null, null, null, null,
                List.of(), null, List.of());
    }

    private static Draft complete() {
        return new Draft(List.of("penang"), false, "2026-04-03", "2026-04-12", "FIXED",
                "Kuala Lumpur", "4000.00", "MYR", 2, 1, List.of("FOOD", "NATURE"), "RELAXED",
                List.of());
    }

    /**
     * A hand-built {@link TripBriefDraft}. The production implementation is a Jackson record in
     * {@code ai/extraction/}, which the domain may not see — so the interface is implemented here
     * instead, which is also the proof that the validation needs nothing from the adapter.
     */
    private record Draft(
            List<String> destinations,
            boolean surpriseMe,
            String startDate,
            String endDate,
            String dateFlexibility,
            String departureCity,
            String budgetAmount,
            String budgetCurrency,
            Integer adults,
            Integer children,
            List<String> interests,
            String pace,
            List<String> ambiguousFields) implements TripBriefDraft {

        Draft withDestinations(List<String> value) {
            return new Draft(value, surpriseMe, startDate, endDate, dateFlexibility, departureCity,
                    budgetAmount, budgetCurrency, adults, children, interests, pace, ambiguousFields);
        }

        Draft withSurpriseMe(boolean value) {
            return new Draft(destinations, value, startDate, endDate, dateFlexibility, departureCity,
                    budgetAmount, budgetCurrency, adults, children, interests, pace, ambiguousFields);
        }

        Draft withDates(String start, String end) {
            return new Draft(destinations, surpriseMe, start, end, dateFlexibility, departureCity,
                    budgetAmount, budgetCurrency, adults, children, interests, pace, ambiguousFields);
        }

        Draft withFlexibility(String value) {
            return new Draft(destinations, surpriseMe, startDate, endDate, value, departureCity,
                    budgetAmount, budgetCurrency, adults, children, interests, pace, ambiguousFields);
        }

        Draft withDepartureCity(String value) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility, value,
                    budgetAmount, budgetCurrency, adults, children, interests, pace, ambiguousFields);
        }

        Draft withBudget(String amount, String currency) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility,
                    departureCity, amount, currency, adults, children, interests, pace,
                    ambiguousFields);
        }

        Draft withParty(Integer adultCount, Integer childCount) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility,
                    departureCity, budgetAmount, budgetCurrency, adultCount, childCount, interests,
                    pace, ambiguousFields);
        }

        Draft withInterests(List<String> value) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility,
                    departureCity, budgetAmount, budgetCurrency, adults, children, value, pace,
                    ambiguousFields);
        }

        Draft withPace(String value) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility,
                    departureCity, budgetAmount, budgetCurrency, adults, children, interests, value,
                    ambiguousFields);
        }

        Draft withAmbiguous(List<String> value) {
            return new Draft(destinations, surpriseMe, startDate, endDate, dateFlexibility,
                    departureCity, budgetAmount, budgetCurrency, adults, children, interests, pace,
                    value);
        }
    }
}
