package com.travelplanner.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.application.trip.TripTestFakes.InMemoryBriefs;
import com.travelplanner.application.trip.TripTestFakes.InMemoryTrips;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripBriefExtractionOutcome;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;
import com.travelplanner.domain.port.TripBriefExtractionPort;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.domain.valueobject.UserContext;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat-authored briefs are the same rows as form-authored ones.
 *
 * <p>What is asserted here is the wiring task 19 adds around task 18's service: the current brief
 * reaches the model, the coverage refusal happens before the write instead of destroying the
 * extraction, a provider failure still returns a usable screen, and the entry point holds no
 * transaction while the model is being called.
 */
class TripBriefExtractionServiceTest {

    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final UserContext STRANGER =
            UserContext.of(UUID.randomUUID(), "stranger@example.com", Role.USER);
    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));
    private static final String VERSION = "trip-brief-extract@v1";

    private InMemoryTrips trips;
    private InMemoryBriefs briefs;
    private RecordingExtractor extractor;
    private TripService tripService;
    private TripBriefExtractionService service;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        briefs = new InMemoryBriefs();
        extractor = new RecordingExtractor();
        SupportedDestinationService destinations = mock(SupportedDestinationService.class);
        when(destinations.listSupported())
                .thenReturn(List.of(TripTestFakes.coveredDestination("penang")));
        TripAccess access = new TripAccess(trips);
        tripService = new TripService(access, trips, briefs);
        service = new TripBriefExtractionService(access,
                new TripBriefService(access, trips, briefs, destinations), extractor, destinations);
    }

    // -------------------------------------------------------------------------------------
    // The happy path
    // -------------------------------------------------------------------------------------

    @Test
    void aCompleteExtractionIsPersistedAndUnblocksResearch() {
        Trip trip = newTrip();
        extractor.returns(extracted(complete()));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.view().status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(result.view().clarification().isSatisfied()).isTrue();
        assertThat(result.view().brief().budget()).isEqualTo(Money.of("4000.00", "MYR"));
        assertThat(briefs.stored(trip.id()).departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(trips.stored(trip.id()).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(result.uncoveredDestinations()).isEmpty();
        assertThat(result.extraction().promptVersion()).isEqualTo(VERSION);
    }

    @Test
    void theBriefsCurrentStateIsWhatTheModelIsAskedToBuildOn() {
        // Extraction is a conversation, not a form post: the second message must be read against
        // what the first one already established.
        Trip trip = newTrip();
        extractor.returns(extracted(TripBriefDetails.empty().withBudget(Money.of("900.00", "MYR"))));
        service.extract(command(trip.id()), OWNER);
        extractor.returns(extracted(complete()));

        service.extract(new ExtractTripBriefCommand(trip.id(), "Make it packed.", "ms"), OWNER);

        assertThat(extractor.requests).hasSize(2);
        assertThat(extractor.requests.get(0).known()).isEqualTo(TripBriefDetails.empty());
        assertThat(extractor.requests.get(1).locale()).isEqualTo("ms");
        assertThat(extractor.requests.get(1).userText()).isEqualTo("Make it packed.");
        assertThat(extractor.requests.get(1).known().budget()).isEqualTo(Money.of("900.00", "MYR"));
    }

    @Test
    void anIncompleteExtractionLeavesTheTripInClarificationWithTypedQuestions() {
        Trip trip = newTrip();
        extractor.returns(extracted(TripBriefDetails.empty().withDates(SPRING)));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.view().status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
        assertThat(result.view().clarification().questions()).hasSize(6);
        assertThat(result.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.EXTRACTED);
    }

    @Test
    void surpriseMeReachesTheCallerEvenThoughNoColumnStoresIt() {
        Trip trip = newTrip();
        extractor.returns(new TripBriefExtraction(complete(), true, List.of(),
                TripBriefExtractionOutcome.EXTRACTED, null, VERSION));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.extraction().surpriseMe()).isTrue();
        assertThat(result.view().brief().destinations()).isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Coverage
    // -------------------------------------------------------------------------------------

    @Test
    void anUncoveredDestinationIsReportedAndDroppedRatherThanLosingTheWholeExtraction() {
        // ADR 010 §4 refuses to rank an uncurated destination. Through the form that is a 404 the
        // user can act on; through chat it must not also discard the six fields they just typed.
        Trip trip = newTrip();
        extractor.returns(extracted(complete().withDestinations(List.of("penang", "osaka"))));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.uncoveredDestinations()).containsExactly("osaka");
        assertThat(result.view().brief().destinations()).containsExactly("penang");
        assertThat(result.view().status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(result.extraction().details().destinations()).containsExactly("penang");
    }

    @Test
    void aBriefNamingOnlyUncoveredDestinationsStillSavesEveryOtherField() {
        Trip trip = newTrip();
        extractor.returns(extracted(complete().withDestinations(List.of("osaka"))));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.uncoveredDestinations()).containsExactly("osaka");
        assertThat(result.view().brief().destinations()).isEmpty();
        assertThat(result.view().brief().budget()).isEqualTo(Money.of("4000.00", "MYR"));
    }

    // -------------------------------------------------------------------------------------
    // Failure
    // -------------------------------------------------------------------------------------

    @Test
    void aProviderFailureReturnsAUsableScreenRatherThanAnError() {
        Trip trip = newTrip();
        extractor.returns(TripBriefExtraction.fallback(
                TripBriefDetails.empty(), AiProviderException.TIMEOUT, VERSION));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(result.extraction().failureCode()).isEqualTo(AiProviderException.TIMEOUT);
        assertThat(result.view().status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
        assertThat(result.view().clarification().questions()).hasSize(7);
    }

    @Test
    void aFallbackDoesNotEraseWhatTheBriefAlreadyHeld() {
        Trip trip = newTrip();
        extractor.returns(extracted(complete()));
        service.extract(command(trip.id()), OWNER);
        TripBriefDetails saved = briefs.stored(trip.id()).details();
        extractor.returns(TripBriefExtraction.fallback(saved, AiProviderException.UNAVAILABLE,
                VERSION));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.view().brief().details()).isEqualTo(saved);
        assertThat(result.view().status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    // -------------------------------------------------------------------------------------
    // Ownership and lifecycle
    // -------------------------------------------------------------------------------------

    @Test
    void anotherUsersTripIsIndistinguishableFromOneThatDoesNotExist() {
        Trip trip = newTrip();

        assertThatThrownBy(() -> service.extract(command(trip.id()), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThat(extractor.requests).isEmpty();
    }

    @Test
    void anArchivedTripIsRefusedAndNoModelCallIsPaidFor() {
        Trip trip = newTrip();
        tripService.archive(new ArchiveTripCommand(trip.id(), trip.version()), OWNER);
        extractor.returns(extracted(complete()));

        assertThatThrownBy(() -> service.extract(command(trip.id()), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aBlankMessageIsRefusedBeforeAnythingIsSpentOnIt() {
        Trip trip = newTrip();

        assertThatThrownBy(() -> service.extract(
                new ExtractTripBriefCommand(trip.id(), "  ", "en"), OWNER))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(extractor.requests).isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // The transaction shape
    // -------------------------------------------------------------------------------------

    /**
     * The rule this class exists to keep: a model call inside a transaction pins a pooled database
     * connection for the whole round trip. Asserted rather than commented, because an annotation is
     * exactly the kind of thing somebody adds while fixing an unrelated lazy-loading problem.
     */
    @Test
    void theEntryPointHoldsNoTransactionWhileTheModelIsCalled() throws NoSuchMethodException {
        Method entryPoint = TripBriefExtractionService.class.getMethod(
                "extract", ExtractTripBriefCommand.class, UserContext.class);

        assertThat(entryPoint.getAnnotation(Transactional.class)).isNull();
        assertThat(entryPoint.getAnnotation(TransactionalWrite.class)).isNull();
        assertThat(TripBriefExtractionService.class.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void theResultNormalisesAnAbsentUncoveredList() {
        TripBriefExtractionResult result = new TripBriefExtractionResult(null,
                TripBriefExtraction.fallback(null, null, VERSION), null);

        assertThat(result.uncoveredDestinations()).isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private Trip newTrip() {
        return tripService.create(new CreateTripCommand("Japan in spring"), OWNER);
    }

    private static ExtractTripBriefCommand command(UUID tripId) {
        return new ExtractTripBriefCommand(tripId, "Penang in April for two.", "en");
    }

    private static TripBriefDetails complete() {
        return TripBriefDetails.empty()
                .withDates(SPRING)
                .withDateFlexibility(DateFlexibility.FIXED)
                .withDepartureCity("Kuala Lumpur")
                .withBudget(Money.of("4000.00", "MYR"))
                .withParty(new PartySize(2, 0))
                .withInterests(List.of(TravelInterest.FOOD))
                .withPace(TravelPace.RELAXED);
    }

    private static TripBriefExtraction extracted(TripBriefDetails details) {
        return new TripBriefExtraction(details, false, List.of(),
                TripBriefExtractionOutcome.EXTRACTED, null, VERSION);
    }

    /** Returns whatever the test scripted, and remembers what it was asked for. */
    private static final class RecordingExtractor implements TripBriefExtractionPort {

        private final List<TripBriefExtractionRequest> requests = new ArrayList<>();
        private TripBriefExtraction next =
                TripBriefExtraction.fallback(TripBriefDetails.empty(), null, VERSION);

        private void returns(TripBriefExtraction extraction) {
            this.next = extraction;
        }

        @Override
        public TripBriefExtraction extract(TripBriefExtractionRequest request) {
            requests.add(request);
            return next;
        }
    }

    /** Referenced so a rename of the question ids fails here too, not only in task 18's tests. */
    @Test
    void theQuestionIdsAreTheOnesTheClarificationFlowRoutesOn() {
        Trip trip = newTrip();
        extractor.returns(extracted(TripBriefDetails.empty()));

        TripBriefExtractionResult result = service.extract(command(trip.id()), OWNER);

        assertThat(result.view().clarification().questions())
                .extracting(ClarificationQuestion::id)
                .contains(ClarificationNeeded.QUESTION_BUDGET_MAX,
                        ClarificationNeeded.QUESTION_TRAVEL_DATES);
    }
}
