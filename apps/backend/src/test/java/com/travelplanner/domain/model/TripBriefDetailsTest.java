package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Normalisation and validation of the editable half of a brief. */
class TripBriefDetailsTest {

    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));

    @Test
    void anEmptyDraftHasNothingDecidedAndNothingInvalid() {
        TripBriefDetails empty = TripBriefDetails.empty();

        assertThat(empty.destinations()).isEmpty();
        assertThat(empty.interests()).isEmpty();
        assertThat(empty.dates()).isNull();
        assertThat(empty.dateFlexibility()).isNull();
        assertThat(empty.departureCity()).isNull();
        assertThat(empty.budget()).isNull();
        assertThat(empty.party()).isNull();
        assertThat(empty.pace()).isNull();
    }

    @Test
    void nullCollectionsBecomeEmptyRatherThanPropagating() {
        TripBriefDetails details = new TripBriefDetails(null, null, null, null, null, null, null, null);

        assertThat(details.destinations()).isEmpty();
        assertThat(details.interests()).isEmpty();
    }

    @Test
    void destinationSlugsAreTrimmedLowercasedAndDeduplicated() {
        // "Kyoto" and "kyoto " are one preference, not two — otherwise the coverage check would
        // reject a slug the user never typed twice.
        TripBriefDetails details = TripBriefDetails.empty()
                .withDestinations(List.of("  Kyoto ", "kyoto", "OSAKA"));

        assertThat(details.destinations()).containsExactly("kyoto", "osaka");
    }

    @Test
    void blankAndNullDestinationRowsAreDroppedRatherThanRejected() {
        // An empty row is a UI artefact, not a statement about anywhere.
        TripBriefDetails details = TripBriefDetails.empty()
                .withDestinations(Arrays.asList("penang", "   ", null));

        assertThat(details.destinations()).containsExactly("penang");
    }

    @Test
    void aPreferenceListNobodyRankedIsRefused() {
        List<String> tooMany = new ArrayList<>(IntStream
                .rangeClosed(0, TripBriefDetails.MAX_DESTINATIONS)
                .mapToObj(index -> "city-" + index)
                .toList());

        assertThatThrownBy(() -> TripBriefDetails.empty().withDestinations(tooMany))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void duplicateAndNullInterestsAreCollapsed() {
        TripBriefDetails details = TripBriefDetails.empty()
                .withInterests(Arrays.asList(TravelInterest.FOOD, TravelInterest.FOOD, null));

        assertThat(details.interests()).containsExactly(TravelInterest.FOOD);
    }

    @Test
    void aBlankDepartureCityIsAbsenceRatherThanAnAnswer() {
        // Collapsing the two here is what keeps the clarification rule from having to know about
        // whitespace: "  " is a field nobody filled in.
        assertThat(TripBriefDetails.empty().withDepartureCity("   ").departureCity()).isNull();
        assertThat(TripBriefDetails.empty().withDepartureCity(" Kuala Lumpur ").departureCity())
                .isEqualTo("Kuala Lumpur");
    }

    @Test
    void anOverlongDepartureCityIsRefused() {
        String tooLong = "x".repeat(TripBriefDetails.MAX_DEPARTURE_CITY_LENGTH + 1);

        assertThatThrownBy(() -> TripBriefDetails.empty().withDepartureCity(tooLong))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void everyFieldCanBeSetIndependentlyAndNoneDisturbsAnother() {
        TripBriefDetails details = TripBriefDetails.empty()
                .withDestinations(List.of("penang"))
                .withDates(SPRING)
                .withDateFlexibility(DateFlexibility.FLEXIBLE_WEEK)
                .withDepartureCity("Kuala Lumpur")
                .withBudget(Money.of("4000.00", "MYR"))
                .withParty(new PartySize(2, 1))
                .withInterests(List.of(TravelInterest.FOOD, TravelInterest.NATURE))
                .withPace(TravelPace.RELAXED);

        assertThat(details.destinations()).containsExactly("penang");
        assertThat(details.dates()).isEqualTo(SPRING);
        assertThat(details.dateFlexibility()).isEqualTo(DateFlexibility.FLEXIBLE_WEEK);
        assertThat(details.departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(details.budget()).isEqualTo(Money.of("4000", "MYR"));
        assertThat(details.party()).isEqualTo(new PartySize(2, 1));
        assertThat(details.interests())
                .containsExactly(TravelInterest.FOOD, TravelInterest.NATURE);
        assertThat(details.pace()).isEqualTo(TravelPace.RELAXED);
    }
}
