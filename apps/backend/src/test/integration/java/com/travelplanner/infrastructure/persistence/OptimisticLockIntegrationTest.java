package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.support.TripWriteFixture;
import com.travelplanner.application.support.TripWriteFixture.CreateTripCommand;
import com.travelplanner.application.support.TripWriteFixture.UpdateBriefBudgetCommand;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * ADR 008 end to end: the agent-versus-user race, on a real database.
 *
 * <p>Both guards are exercised, because they catch different things. The explicit
 * {@code requireVersion} check catches a caller editing stale state — the debounced brief form or a
 * stale LLM tool call — and is the only one that can report {@code current_version}. The JPA
 * {@code @Version} column catches two transactions that both read the same version and commit.
 * Either alone leaves a real lost-update path open.
 */
@Import(TripWriteFixture.class)
class OptimisticLockIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TripWriteFixture fixture;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private TripRepositoryPort trips;

    @Autowired
    private TripBriefRepositoryPort briefs;

    @Test
    void aSuccessfulWriteIncrementsTheVersion() {
        UUID tripId = tripWithBrief();
        int before = briefs.findByTripId(tripId).orElseThrow().version();

        TripBrief updated = fixture.updateBriefBudget(
                new UpdateBriefBudgetCommand(tripId, before, Money.of("5000.00", "USD")));

        assertThat(updated.version()).isEqualTo(before + 1);
        assertThat(updated.budgetIfPresent()).contains(Money.of("5000.00", "USD"));
    }

    @Test
    void aStaleExpectedVersionIsRejectedWithTheCurrentVersionInTheDetails() {
        UUID tripId = tripWithBrief();
        int original = briefs.findByTripId(tripId).orElseThrow().version();

        // The agent writes first.
        fixture.updateBriefBudget(
                new UpdateBriefBudgetCommand(tripId, original, Money.of("5000.00", "USD")));

        // The user's debounced form autosave lands second, still based on `original`.
        assertThatThrownBy(() -> fixture.updateBriefBudget(
                new UpdateBriefBudgetCommand(tripId, original, Money.of("9999.00", "USD"))))
                .isInstanceOf(VersionConflictException.class)
                .satisfies(conflict -> assertThat(((VersionConflictException) conflict).details())
                        .isEqualTo(Map.of("current_version", original + 1)));

        // No implicit winner: the loser changed nothing.
        assertThat(briefs.findByTripId(tripId).orElseThrow().budgetIfPresent())
                .contains(Money.of("5000.00", "USD"));
    }

    @Test
    void twoWritersHoldingTheSameVersionCannotBothCommit() {
        UUID tripId = tripWithBrief();
        // Both readers hold the same snapshot — the situation the @Version column exists for.
        TripBrief readByAgent = briefs.findByTripId(tripId).orElseThrow();
        TripBrief readByUser = briefs.findByTripId(tripId).orElseThrow();

        briefs.save(readByAgent.withBudget(Money.of("5000.00", "USD"), Instant.now()));

        assertThatThrownBy(() ->
                briefs.save(readByUser.withBudget(Money.of("9999.00", "USD"), Instant.now())))
                .isInstanceOf(VersionConflictException.class)
                .satisfies(conflict -> assertThat(((VersionConflictException) conflict).details())
                        .isEqualTo(Map.of("current_version", readByUser.version() + 1)));
    }

    @Test
    void theTripAggregateIsVersionedToo() {
        UUID userId = persistedUserId();
        Trip stored = trips.save(Trip.create(userId, "Kyoto", Instant.now()));

        Trip firstWriter = trips.save(stored.rename("Kyoto and Osaka", Instant.now()));
        assertThat(firstWriter.version()).isEqualTo(stored.version() + 1);

        assertThatThrownBy(() -> trips.save(stored.rename("Nara", Instant.now())))
                .isInstanceOf(VersionConflictException.class);
    }

    private UUID tripWithBrief() {
        Trip trip = fixture.createTripWithBrief(new CreateTripCommand(
                persistedUserId(), "Japan in spring", Money.of("4000.10", "USD")));
        return trip.id();
    }

    private UUID persistedUserId() {
        Instant now = Instant.now();
        return users.save(new User(UUID.randomUUID(), null,
                "user-" + UUID.randomUUID() + "@example.test", null, false, Role.USER, true, 0,
                null, null, now, now)).id();
    }
}
