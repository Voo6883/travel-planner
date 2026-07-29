package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

/** The input bounds, held once so no adapter has to remember them. */
class TripBriefExtractionRequestTest {

    @Test
    void aBlankMessageIsNotSomethingToExtractFrom() {
        assertThatThrownBy(() -> TripBriefExtractionRequest.of("   ", "en"))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> TripBriefExtractionRequest.of(null, "en"))
                .isInstanceOf(ValidationFailedException.class);
    }

    /**
     * Refused, not truncated. A truncated message drops the half that named the budget and then
     * asks for a budget the traveller had just given, which reads as the product not listening.
     */
    @Test
    void anOverLongMessageIsRefusedRatherThanQuietlyCut() {
        String tooLong = "a".repeat(TripBriefExtractionRequest.MAX_USER_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> TripBriefExtractionRequest.of(tooLong, "en"))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(refusal -> assertThat(((ValidationFailedException) refusal).details())
                        .hasToString("{fields={user_text=must be at most 4000 characters}}"));
    }

    @Test
    void aMessageExactlyAtTheLimitIsAccepted() {
        String atLimit = "a".repeat(TripBriefExtractionRequest.MAX_USER_TEXT_LENGTH);

        assertThat(TripBriefExtractionRequest.of(atLimit, "en").userText()).hasSize(4000);
    }

    @Test
    void bothShippedLanguagesSurviveAndAnythingElseReadsAsEnglish() {
        assertThat(TripBriefExtractionRequest.of("hi", "en").locale()).isEqualTo("en");
        assertThat(TripBriefExtractionRequest.of("hi", "ms").locale()).isEqualTo("ms");
        assertThat(TripBriefExtractionRequest.of("hi", "de").locale()).isEqualTo("en");
        assertThat(TripBriefExtractionRequest.of("hi", null).locale()).isEqualTo("en");
    }

    @Test
    void aRegionalTagIsReducedToItsLanguage() {
        assertThat(TripBriefExtractionRequest.of("hi", "ms-MY").locale()).isEqualTo("ms");
        assertThat(TripBriefExtractionRequest.of("hi", " EN-GB ").locale()).isEqualTo("en");
    }

    @Test
    void anAbsentBriefBecomesAnEmptyOneRatherThanANull() {
        assertThat(new TripBriefExtractionRequest("hi", "en", null).known())
                .isEqualTo(TripBriefDetails.empty());
    }

    @Test
    void whatTheBriefAlreadyHoldsIsCarriedThroughUnchanged() {
        TripBriefDetails known = TripBriefDetails.empty().withBudget(Money.of("900.00", "MYR"));

        assertThat(new TripBriefExtractionRequest("hi", "en", known).known()).isEqualTo(known);
    }
}
