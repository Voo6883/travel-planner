package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The brief aggregate: identity, absence, immutability, and the version ADR 008 depends on. */
class TripBriefTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void aNewBriefIsEmptyUnpersistedAndAttachedToItsTrip() {
        TripBrief brief = TripBrief.createFor(TRIP, NOW);

        assertThat(brief.tripId()).isEqualTo(TRIP);
        assertThat(brief.version()).isZero();
        assertThat(brief.createdAt()).isEqualTo(NOW);
        assertThat(brief.destinations()).isEmpty();
        assertThat(brief.surpriseMe()).isFalse();
        assertThat(brief.interests()).isEmpty();
        assertThat(brief.budgetIfPresent()).isEmpty();
        assertThat(brief.datesIfPresent()).isEmpty();
    }

    @Test
    void detailsRoundTripThroughTheAggregateUnchanged() {
        TripBriefDetails details = TripBriefDetails.empty()
                .withDestinations(List.of("penang"))
                .withBudget(Money.of("4000.00", "MYR"));

        TripBrief brief = TripBrief.createFor(TRIP, NOW).withDetails(details, LATER);

        assertThat(brief.details()).isEqualTo(details);
    }

    @Test
    void aWriteProducesANewInstanceAndLeavesTheOriginalUntouched() {
        // Immutability is what makes the version trustworthy: a setter could change the aggregate
        // between the moment its version was read and the moment it was written.
        TripBrief original = TripBrief.createFor(TRIP, NOW);

        TripBrief updated = original.withBudget(Money.of("100.00", "MYR"), LATER);

        assertThat(original.budgetIfPresent()).isEmpty();
        assertThat(updated.budgetIfPresent()).contains(Money.of("100", "MYR"));
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(updated.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void aWriteCarriesTheVersionThroughRatherThanGuessingTheIncrement() {
        // The increment belongs to the persistence provider's @Version handling. A domain method
        // that guessed it would produce an aggregate whose version matches no row.
        TripBrief stored = new TripBrief(UUID.randomUUID(), TRIP, List.of(), false, null, null,
                null, null, null, List.of(), null, 7, NOW, NOW);

        assertThat(stored.withDates(DateRange.singleDay(LocalDate.of(2026, 4, 3)), LATER).version())
                .isEqualTo(7);
    }

    @Test
    void aNegativeVersionIsRefused() {
        assertThatThrownBy(() -> new TripBrief(UUID.randomUUID(), TRIP, List.of(), false, null,
                null, null, null, null, List.of(), null, -1, NOW, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void surpriseMeOnTheAggregateAlsoClearsDestinations() {
        TripBrief brief = TripBrief.createFor(TRIP, NOW)
                .withDetails(TripBriefDetails.empty()
                        .withDestinations(List.of("penang"))
                        .withSurpriseMe(true), LATER);

        assertThat(brief.surpriseMe()).isTrue();
        assertThat(brief.destinations()).isEmpty();
        assertThat(brief.details().surpriseMe()).isTrue();
    }

    @Test
    void applyingNoDetailsAtAllIsRefusedRatherThanClearingTheBrief() {
        TripBrief brief = TripBrief.createFor(TRIP, NOW);

        assertThatThrownBy(() -> brief.withDetails(null, LATER))
                .isInstanceOf(NullPointerException.class);
    }
}
