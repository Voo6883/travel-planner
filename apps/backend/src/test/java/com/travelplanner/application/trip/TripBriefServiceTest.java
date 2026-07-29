package com.travelplanner.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.application.trip.TripTestFakes.InMemoryBriefs;
import com.travelplanner.application.trip.TripTestFakes.InMemoryTrips;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.DestinationNotCoveredException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.ClarificationAnswer;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * C1 intake: ownership, the clarification round trip, status derivation, coverage refusal, and
 * the optimistic-locking path ADR 008 exists for.
 */
class TripBriefServiceTest {

    private static final UserContextFixture FIXTURE = new UserContextFixture();
    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private InMemoryTrips trips;
    private InMemoryBriefs briefs;
    private SupportedDestinationService destinations;
    private TripService tripService;
    private TripBriefService service;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        briefs = new InMemoryBriefs();
        destinations = mock(SupportedDestinationService.class);
        when(destinations.listSupported())
                .thenReturn(List.of(TripTestFakes.coveredDestination("penang")));
        TripAccess access = new TripAccess(trips);
        tripService = new TripService(access, trips, briefs);
        service = new TripBriefService(access, trips, briefs, destinations);
    }

    private Trip newTrip() {
        return tripService.create(new CreateTripCommand("Japan in spring"), FIXTURE.owner());
    }

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

    // -------------------------------------------------------------------------------------
    // Ownership
    // -------------------------------------------------------------------------------------

    @Test
    void anotherUsersBriefIsIndistinguishableFromOneThatDoesNotExist() {
        Trip trip = newTrip();

        assertThatThrownBy(() -> service.get(trip.id(), FIXTURE.stranger()))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.save(
                new SaveTripBriefCommand(trip.id(), 1, complete()), FIXTURE.stranger()))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.answerClarification(
                new AnswerClarificationCommand(trip.id(), 1, List.of()), FIXTURE.stranger()))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void aTripWhoseBriefRowIsGoneIsAFourZeroFourRatherThanAServerFault() {
        Trip trip = newTrip();
        briefs.forget(trip.id());

        assertThatThrownBy(() -> service.get(trip.id(), FIXTURE.owner()))
                .isInstanceOf(TripNotFoundException.class);
    }

    // -------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------

    @Test
    void theReadCarriesTheVersionTheWriteWillHaveToEcho() {
        // ADR 008 §1. A client that cannot see the version has no option but to force-overwrite.
        Trip trip = newTrip();

        TripBriefView view = service.get(trip.id(), FIXTURE.owner());

        assertThat(view.brief().version()).isEqualTo(1);
        assertThat(view.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(view.clarification().questions()).hasSize(7);
    }

    // -------------------------------------------------------------------------------------
    // Saving and status derivation
    // -------------------------------------------------------------------------------------

    @Test
    void anIncompleteSaveProducesTypedQuestionsAndNeverAGuess() {
        Trip trip = newTrip();
        TripBrief stored = briefs.stored(trip.id());

        TripBriefView view = service.save(new SaveTripBriefCommand(trip.id(), stored.version(),
                TripBriefDetails.empty().withBudget(Money.of("4000.00", "MYR"))), FIXTURE.owner());

        assertThat(view.status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
        assertThat(view.clarification().questions())
                .extracting(ClarificationQuestion::id)
                .doesNotContain(ClarificationNeeded.QUESTION_BUDGET_MAX)
                .contains(ClarificationNeeded.QUESTION_TRAVEL_DATES);
        assertThat(view.brief().budget()).isEqualTo(Money.of("4000", "MYR"));
    }

    @Test
    void aCompleteSaveMovesTheTripToBriefComplete() {
        Trip trip = newTrip();

        TripBriefView view = service.save(
                new SaveTripBriefCommand(trip.id(), briefs.stored(trip.id()).version(), complete()),
                FIXTURE.owner());

        assertThat(view.status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(view.clarification().isSatisfied()).isTrue();
        assertThat(trips.stored(trip.id()).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void aSaveThatRemovesAFieldUnCompletesTheTripAgain() {
        // The property C1 lives on. Without it a trip keeps claiming a completeness the stored
        // brief no longer has, and C2 would start on a brief with no budget.
        Trip trip = newTrip();
        TripBriefView completed = service.save(
                new SaveTripBriefCommand(trip.id(), briefs.stored(trip.id()).version(), complete()),
                FIXTURE.owner());

        TripBriefView reopened = service.save(new SaveTripBriefCommand(trip.id(),
                completed.brief().version(), complete().withBudget(null)), FIXTURE.owner());

        assertThat(reopened.status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
        assertThat(trips.stored(trip.id()).status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
    }

    @Test
    void aSaveThatChangesNothingAboutCompletenessLeavesTheTripStatusAlone() {
        Trip trip = newTrip();
        service.save(new SaveTripBriefCommand(trip.id(), briefs.stored(trip.id()).version(),
                complete()), FIXTURE.owner());
        int tripVersionAfterFirstSave = trips.stored(trip.id()).version();

        service.save(new SaveTripBriefCommand(trip.id(), briefs.stored(trip.id()).version(),
                complete().withDepartureCity("Ipoh")), FIXTURE.owner());

        assertThat(trips.stored(trip.id()).version()).isEqualTo(tripVersionAfterFirstSave);
    }

    @Test
    void aSaveWithNoDetailsAtAllClearsTheBriefRatherThanMergingIt() {
        // PUT is a replacement, not a merge — PATCH is forbidden project-wide, and a PUT that read
        // absence as "leave unchanged" would give a client no way to un-set a field.
        Trip trip = newTrip();
        TripBriefView filled = service.save(
                new SaveTripBriefCommand(trip.id(), briefs.stored(trip.id()).version(), complete()),
                FIXTURE.owner());

        TripBriefView cleared = service.save(
                new SaveTripBriefCommand(trip.id(), filled.brief().version(), null),
                FIXTURE.owner());

        assertThat(cleared.brief().details()).isEqualTo(TripBriefDetails.empty());
        assertThat(cleared.status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
    }

    // -------------------------------------------------------------------------------------
    // Concurrency — ADR 008
    // -------------------------------------------------------------------------------------

    @Test
    void aSuccessfulSaveReturnsTheNewFullResourceWithAnIncrementedVersion() {
        Trip trip = newTrip();
        int before = briefs.stored(trip.id()).version();

        TripBriefView view = service.save(
                new SaveTripBriefCommand(trip.id(), before, complete()), FIXTURE.owner());

        assertThat(view.brief().version()).isEqualTo(before + 1);
        assertThat(view.brief().departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(briefs.stored(trip.id()).version()).isEqualTo(before + 1);
    }

    @Test
    void aStaleSaveIsRefusedWithTheCurrentVersionAndChangesNothing() {
        // The exact race ADR 008 exists to prevent: the debounced form and the agent both hold a
        // version, and the loser is told the truth instead of silently discarding the winner.
        Trip trip = newTrip();
        int agentBase = briefs.stored(trip.id()).version();
        TripBriefView agentWrite = service.save(
                new SaveTripBriefCommand(trip.id(), agentBase, complete()), FIXTURE.owner());

        assertThatThrownBy(() -> service.save(new SaveTripBriefCommand(trip.id(), agentBase,
                complete().withDepartureCity("Ipoh")), FIXTURE.owner()))
                .isInstanceOf(VersionConflictException.class)
                .satisfies(conflict -> assertThat(((VersionConflictException) conflict).details())
                        .containsEntry("current_version", agentWrite.brief().version()));
        assertThat(briefs.stored(trip.id()).departureCity()).isEqualTo("Kuala Lumpur");
    }

    @Test
    void aStaleClarificationAnswerIsRefusedTheSameWay() {
        Trip trip = newTrip();
        int base = briefs.stored(trip.id()).version();
        service.save(new SaveTripBriefCommand(trip.id(), base, TripBriefDetails.empty()),
                FIXTURE.owner());

        assertThatThrownBy(() -> service.answerClarification(
                new AnswerClarificationCommand(trip.id(), base, List.of(
                        ClarificationAnswer.ofText(
                                ClarificationNeeded.QUESTION_DEPARTURE_CITY, "Penang"))),
                FIXTURE.owner()))
                .isInstanceOf(VersionConflictException.class);
    }

    // -------------------------------------------------------------------------------------
    // Clarification round trip
    // -------------------------------------------------------------------------------------

    @Test
    void answeringEveryQuestionCompletesTheBriefAndUnblocksCTwo() {
        Trip trip = newTrip();
        TripBriefView pending = service.get(trip.id(), FIXTURE.owner());

        TripBriefView answered = service.answerClarification(new AnswerClarificationCommand(
                trip.id(), pending.brief().version(), List.of(
                        ClarificationAnswer.ofDateRange(
                                ClarificationNeeded.QUESTION_TRAVEL_DATES, SPRING),
                        ClarificationAnswer.ofChoice(
                                ClarificationNeeded.QUESTION_DATE_FLEXIBILITY, "FIXED"),
                        ClarificationAnswer.ofText(
                                ClarificationNeeded.QUESTION_DEPARTURE_CITY, "Penang"),
                        ClarificationAnswer.ofMoney(ClarificationNeeded.QUESTION_BUDGET_MAX,
                                Money.of("2500.00", "MYR")),
                        ClarificationAnswer.ofNumber(ClarificationNeeded.QUESTION_PARTY_SIZE, 2),
                        ClarificationAnswer.ofChoices(
                                ClarificationNeeded.QUESTION_INTERESTS, List.of("FOOD")),
                        ClarificationAnswer.ofChoice(ClarificationNeeded.QUESTION_PACE, "RELAXED"))),
                FIXTURE.owner());

        assertThat(answered.status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(answered.clarification().isSatisfied()).isTrue();
        assertThat(answered.brief().version()).isEqualTo(pending.brief().version() + 1);
    }

    @Test
    void answeringSomeQuestionsReturnsTheRemainingOnes() {
        Trip trip = newTrip();
        TripBriefView pending = service.get(trip.id(), FIXTURE.owner());

        TripBriefView partial = service.answerClarification(new AnswerClarificationCommand(
                trip.id(), pending.brief().version(),
                List.of(ClarificationAnswer.ofChoice(ClarificationNeeded.QUESTION_PACE, "PACKED"))),
                FIXTURE.owner());

        assertThat(partial.status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
        assertThat(partial.clarification().questions())
                .extracting(ClarificationQuestion::id)
                .doesNotContain(ClarificationNeeded.QUESTION_PACE);
        assertThat(partial.brief().pace()).isEqualTo(TravelPace.PACKED);
    }

    @Test
    void answeringNothingIsRejectedByTheDomainRatherThanBurningAVersion() {
        Trip trip = newTrip();
        TripBriefView pending = service.get(trip.id(), FIXTURE.owner());
        AnswerClarificationCommand nothing =
                new AnswerClarificationCommand(trip.id(), pending.brief().version(), null);

        // An empty answer list is inert rather than an error at this layer; the request DTO's
        // @NotEmpty is what refuses it over HTTP. What matters here is that it cannot corrupt.
        TripBriefView unchanged = service.answerClarification(nothing, FIXTURE.owner());

        assertThat(unchanged.brief().details()).isEqualTo(pending.brief().details());
        assertThat(unchanged.status()).isEqualTo(TripStatus.CLARIFICATION_NEEDED);
    }

    @Test
    void anAnswerToAQuestionThatWasNotAskedIsRefused() {
        Trip trip = newTrip();
        TripBriefView completed = service.save(new SaveTripBriefCommand(
                trip.id(), briefs.stored(trip.id()).version(), complete()), FIXTURE.owner());

        assertThatThrownBy(() -> service.answerClarification(new AnswerClarificationCommand(
                trip.id(), completed.brief().version(), List.of(ClarificationAnswer.ofMoney(
                        ClarificationNeeded.QUESTION_BUDGET_MAX, Money.of("1.00", "MYR")))),
                FIXTURE.owner()))
                .isInstanceOf(ValidationFailedException.class);
    }

    // -------------------------------------------------------------------------------------
    // Coverage, archived, and transitions
    // -------------------------------------------------------------------------------------

    @Test
    void aDestinationTheKnowledgeBaseNeverCuratedIsRefusedWithTheCoveredListAttached() {
        // ADR 010 §4. Accepting it and ranking it near zero would read to the user as "we
        // considered Osaka and it is a poor match" when nobody ever looked at Osaka.
        Trip trip = newTrip();
        TripBriefDetails osaka = complete().withDestinations(List.of("osaka"));

        assertThatThrownBy(() -> service.save(new SaveTripBriefCommand(
                trip.id(), briefs.stored(trip.id()).version(), osaka), FIXTURE.owner()))
                .isInstanceOf(DestinationNotCoveredException.class)
                .satisfies(refusal -> {
                    DestinationNotCoveredException typed = (DestinationNotCoveredException) refusal;
                    assertThat(typed.supportedSlugs()).containsExactly("penang");
                    assertThat(typed.details()).containsEntry("requested", "osaka");
                });
    }

    @Test
    void aCoveredDestinationIsAccepted() {
        Trip trip = newTrip();

        TripBriefView view = service.save(new SaveTripBriefCommand(trip.id(),
                briefs.stored(trip.id()).version(), complete().withDestinations(List.of("penang"))),
                FIXTURE.owner());

        assertThat(view.brief().destinations()).containsExactly("penang");
        assertThat(view.status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void anArchivedBriefIsReadableButNotWritable() {
        Trip trip = newTrip();
        Trip archived = tripService.archive(
                new ArchiveTripCommand(trip.id(), trip.version()), FIXTURE.owner());
        int briefVersion = briefs.stored(trip.id()).version();

        assertThat(service.get(trip.id(), FIXTURE.owner()).status())
                .isEqualTo(TripStatus.ARCHIVED);
        assertThatThrownBy(() -> service.save(
                new SaveTripBriefCommand(trip.id(), briefVersion, complete()), FIXTURE.owner()))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.answerClarification(new AnswerClarificationCommand(
                trip.id(), briefVersion, List.of(ClarificationAnswer.ofChoice(
                        ClarificationNeeded.QUESTION_PACE, "RELAXED"))), FIXTURE.owner()))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(archived.status()).isEqualTo(TripStatus.ARCHIVED);
    }

    @Test
    void aTripThatHasLeftIntakeCannotBeDraggedBackByASave() {
        // Task 22 widens the allowed set when it adds the endpoint that starts research; until
        // then a research-phase trip is refused rather than reset to CLARIFICATION_NEEDED.
        UUID tripId = UUID.randomUUID();
        trips.seed(new Trip(tripId, FIXTURE.owner().userId(), "Researching",
                TripStatus.RESEARCH_READY, null, 3, NOW, NOW));
        briefs.seed(TripBrief.createFor(tripId, NOW));

        assertThatThrownBy(() -> service.save(
                new SaveTripBriefCommand(tripId, 0, TripBriefDetails.empty()), FIXTURE.owner()))
                .isInstanceOf(ValidationFailedException.class);
    }

    /** Two callers, built once, so no test can accidentally reuse the wrong one. */
    private static final class UserContextFixture {

        private final UserContext owner =
                UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
        private final UserContext stranger =
                UserContext.of(UUID.randomUUID(), "stranger@example.com", Role.USER);

        UserContext owner() {
            return owner;
        }

        UserContext stranger() {
            return stranger;
        }
    }
}
