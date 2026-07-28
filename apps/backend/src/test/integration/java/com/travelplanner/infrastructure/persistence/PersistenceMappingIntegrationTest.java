package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round-trips domain aggregates through the real schema.
 *
 * <p>The assertions that matter are the ones a naive mapping would fail: a money amount that binary
 * floating point cannot hold, a value object reassembled from two columns, and a lookup for
 * somebody else's trip.
 */
class PersistenceMappingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private TripRepositoryPort trips;

    @Autowired
    private TripBriefRepositoryPort briefs;

    @Test
    void aUserRoundTripsThroughEveryColumnIncludingTheAdr009RevocationFields() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        User saved = users.save(new User(UUID.randomUUID(), "planner_" + suffix(), email(),
                "$2a$12$notarealhash", true, Role.USER, true, 3, now, null, now, now));

        User loaded = users.findById(saved.id()).orElseThrow();

        assertThat(loaded.tokenVersion()).isEqualTo(3);
        assertThat(loaded.sessionsValidAfter()).isEqualTo(now);
        assertThat(loaded.role()).isEqualTo(Role.USER);
        assertThat(loaded.emailVerified()).isTrue();
        assertThat(loaded.isOAuthOnly()).isFalse();
    }

    @Test
    void findByEmailIsCaseInsensitiveToMatchTheUniqueIndex() {
        String address = email();
        users.save(newUser(address));

        assertThat(users.findByEmailIgnoreCase(address.toUpperCase(java.util.Locale.ROOT)))
                .isPresent();
    }

    @Test
    void moneySurvivesTheRoundTripExactly() {
        Trip trip = trips.save(Trip.create(persistedUserId(), "Japan in spring", Instant.now()));
        // 4000.10 has no exact binary floating-point representation. A `double` column would read
        // back 4000.099999999999 and the assertion below would fail.
        Money budget = Money.of("4000.10", "USD");

        briefs.save(TripBrief.createFor(trip.id(), Instant.now()).withBudget(budget, Instant.now()));

        TripBrief loaded = briefs.findByTripId(trip.id()).orElseThrow();
        assertThat(loaded.budgetIfPresent()).contains(budget);
        assertThat(loaded.budgetIfPresent().orElseThrow().amount())
                .isEqualByComparingTo("4000.10");
    }

    @Test
    void aDateRangeIsReassembledFromItsTwoColumns() {
        Trip trip = trips.save(Trip.create(persistedUserId(), "Kyoto", Instant.now()));
        DateRange dates = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5));

        briefs.save(TripBrief.createFor(trip.id(), Instant.now()).withDates(dates, Instant.now()));

        TripBrief loaded = briefs.findByTripId(trip.id()).orElseThrow();
        assertThat(loaded.datesIfPresent()).contains(dates);
        assertThat(loaded.datesIfPresent().orElseThrow().nights()).isEqualTo(4);
    }

    @Test
    void anAbsentValueObjectStaysAbsentRatherThanBecomingAHalfBuiltOne() {
        Trip trip = trips.save(Trip.create(persistedUserId(), "Undecided", Instant.now()));

        briefs.save(TripBrief.createFor(trip.id(), Instant.now()));

        TripBrief loaded = briefs.findByTripId(trip.id()).orElseThrow();
        assertThat(loaded.budgetIfPresent()).isEmpty();
        assertThat(loaded.datesIfPresent()).isEmpty();
    }

    @Test
    void aTripIsInvisibleToAnyUserButItsOwner() {
        UUID owner = persistedUserId();
        UUID stranger = persistedUserId();
        Trip trip = trips.save(Trip.create(owner, "Kyoto", Instant.now()));

        assertThat(trips.findByIdAndUserId(trip.id(), owner)).isPresent();
        // Empty, not an exception: a distinguishable failure would let a stranger probe for the
        // existence of somebody else's trip (PLAN §4.0.2-L).
        assertThat(trips.findByIdAndUserId(trip.id(), stranger)).isEmpty();
        assertThat(trips.findAllByUserId(stranger)).isEmpty();
    }

    @Test
    void aTripRoundTripsItsStatusAsANameRatherThanAnOrdinal() {
        UUID owner = persistedUserId();
        Trip archived = trips.save(
                Trip.create(owner, "Old trip", Instant.now()).withStatus(TripStatus.ARCHIVED, Instant.now()));

        Optional<Trip> loaded = trips.findByIdAndUserId(archived.id(), owner);

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().status()).isEqualTo(TripStatus.ARCHIVED);
    }

    @Test
    void deletingATripCascadesToItsBriefButOnlyForTheOwner() {
        UUID owner = persistedUserId();
        Trip trip = trips.save(Trip.create(owner, "Kyoto", Instant.now()));
        briefs.save(TripBrief.createFor(trip.id(), Instant.now()));

        assertThat(trips.deleteByIdAndUserId(trip.id(), UUID.randomUUID())).isFalse();
        assertThat(trips.deleteByIdAndUserId(trip.id(), owner)).isTrue();
        assertThat(briefs.findByTripId(trip.id())).isEmpty();
    }

    private UUID persistedUserId() {
        return users.save(newUser(email())).id();
    }

    private User newUser(String address) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), null, address, null, false, Role.USER, true, 0, null,
                null, now, now);
    }

    private static String email() {
        return "user-" + suffix() + "@example.test";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
