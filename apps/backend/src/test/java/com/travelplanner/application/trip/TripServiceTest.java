package com.travelplanner.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.trip.TripTestFakes.InMemoryBriefs;
import com.travelplanner.application.trip.TripTestFakes.InMemoryTrips;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Trip lifecycle: ownership scoping, archived read-only, and the ADR 008 locking path. */
class TripServiceTest {

    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final UserContext STRANGER =
            UserContext.of(UUID.randomUUID(), "stranger@example.com", Role.USER);
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private InMemoryTrips trips;
    private InMemoryBriefs briefs;
    private TripService service;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        briefs = new InMemoryBriefs();
        service = new TripService(new TripAccess(trips), trips, briefs);
    }

    @Test
    void creatingATripAlsoCreatesItsEmptyBrief() {
        // One transaction, two rows, in the documented lock order. A trip whose brief row was
        // missing would make every later read a "does it exist yet?" branch.
        Trip created = service.create(new CreateTripCommand("Japan in spring"), OWNER);

        assertThat(created.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(created.userId()).isEqualTo(OWNER.userId());
        assertThat(created.version()).isEqualTo(1);
        assertThat(briefs.stored(created.id())).isNotNull();
        assertThat(briefs.stored(created.id()).details().destinations()).isEmpty();
    }

    @Test
    void aTripOwnedBySomebodyElseIsIndistinguishableFromOneThatDoesNotExist() {
        // PLAN §4.0.2-L. A 403 here would answer "does this id exist?" for anyone who asked.
        Trip owned = service.create(new CreateTripCommand("Kyoto"), OWNER);

        assertThatThrownBy(() -> service.get(owned.id(), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), OWNER))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void theListNeverLeaksAnotherUsersTrips() {
        service.create(new CreateTripCommand("Mine"), OWNER);
        service.create(new CreateTripCommand("Theirs"), STRANGER);

        assertThat(service.list(OWNER)).extracting(Trip::name).containsExactly("Mine");
        assertThat(service.list(STRANGER)).extracting(Trip::name).containsExactly("Theirs");
    }

    @Test
    void theListIsNewestFirst() {
        trips.seed(new Trip(UUID.randomUUID(), OWNER.userId(), "Older", TripStatus.DRAFT, null,
                1, NOW, NOW));
        trips.seed(new Trip(UUID.randomUUID(), OWNER.userId(), "Newer", TripStatus.DRAFT, null,
                1, NOW.plusSeconds(60), NOW.plusSeconds(60)));

        assertThat(service.list(OWNER)).extracting(Trip::name).containsExactly("Newer", "Older");
    }

    @Test
    void aSuccessfulRenameReturnsTheNewFullResourceWithAnIncrementedVersion() {
        // ADR 008 §2: "every mutation returns the new full resource with its incremented version".
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);

        Trip renamed = service.rename(
                new RenameTripCommand(created.id(), created.version(), "Kyoto and Osaka"), OWNER);

        assertThat(renamed.name()).isEqualTo("Kyoto and Osaka");
        assertThat(renamed.version()).isEqualTo(created.version() + 1);
        assertThat(trips.stored(created.id()).name()).isEqualTo("Kyoto and Osaka");
    }

    @Test
    void aStaleExpectedVersionIsRefusedAndCarriesTheCurrentOne() {
        // The whole point of ADR 008: the loser is told the truth so it can re-read and re-apply,
        // rather than being told only that it lost.
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);
        Trip advanced = service.rename(
                new RenameTripCommand(created.id(), created.version(), "Kyoto v2"), OWNER);

        assertThatThrownBy(() -> service.rename(
                new RenameTripCommand(created.id(), created.version(), "Kyoto v3"), OWNER))
                .isInstanceOf(VersionConflictException.class)
                .satisfies(conflict -> assertThat(((VersionConflictException) conflict).details())
                        .containsEntry("current_version", advanced.version()));
        assertThat(trips.stored(created.id()).name()).isEqualTo("Kyoto v2");
    }

    @Test
    void renamingIsRefusedWhenTheNameIsNotOne() {
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);

        assertThatThrownBy(() -> service.rename(
                new RenameTripCommand(created.id(), created.version(), "   "), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void archivingMakesTheTripReadOnlyForEveryFurtherWrite() {
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);

        Trip archived = service.archive(
                new ArchiveTripCommand(created.id(), created.version()), OWNER);

        assertThat(archived.status()).isEqualTo(TripStatus.ARCHIVED);
        assertThat(archived.version()).isEqualTo(created.version() + 1);
        assertThatThrownBy(() -> service.rename(
                new RenameTripCommand(created.id(), archived.version(), "Kyoto v2"), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void archivingTwiceIsARefusalRatherThanASilentSuccess() {
        // The second call is a client acting on a stale view, and ADR 008's position is that such
        // a client is told rather than quietly agreed with.
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);
        Trip archived = service.archive(
                new ArchiveTripCommand(created.id(), created.version()), OWNER);

        assertThatThrownBy(() -> service.archive(
                new ArchiveTripCommand(created.id(), archived.version()), OWNER))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void archivingRefusesAStaleVersionToo() {
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);
        service.rename(new RenameTripCommand(created.id(), created.version(), "Kyoto v2"), OWNER);

        assertThatThrownBy(() -> service.archive(
                new ArchiveTripCommand(created.id(), created.version()), OWNER))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    void aStrangerCanNeitherRenameArchiveNorDelete() {
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);

        assertThatThrownBy(() -> service.rename(
                new RenameTripCommand(created.id(), created.version(), "Theirs"), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.archive(
                new ArchiveTripCommand(created.id(), created.version()), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.delete(created.id(), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThat(trips.stored(created.id())).isNotNull();
    }

    @Test
    void deletingRemovesTheCallersTripAndReportsAMissingOneAsNotFound() {
        Trip created = service.create(new CreateTripCommand("Kyoto"), OWNER);

        service.delete(created.id(), OWNER);

        assertThat(trips.stored(created.id())).isNull();
        assertThatThrownBy(() -> service.delete(created.id(), OWNER))
                .isInstanceOf(TripNotFoundException.class);
    }
}
