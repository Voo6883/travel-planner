package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.support.TripWriteFixture;
import com.travelplanner.application.support.TripWriteFixture.CreateTripCommand;
import com.travelplanner.application.support.TripWriteFixture.FixtureFailure;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Proves the {@code @TransactionalWrite} template against a real database
 * (PLAN §4.0.2-E2, task 07 Validation: "rollback on forced failure").
 *
 * <p>The forced failure is a <em>checked</em> exception. That is the case a plain
 * {@code @Transactional} gets wrong: Spring's default rollback rules cover unchecked exceptions
 * only, so without {@code rollbackFor = Exception.class} the trip and its brief would both commit
 * on the way out of a failing method.
 */
@Import(TripWriteFixture.class)
class TransactionBoundaryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TripWriteFixture fixture;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private TripRepositoryPort trips;

    @Autowired
    private TripBriefRepositoryPort briefs;

    @Test
    void bothTablesCommitTogetherWhenTheUseCaseSucceeds() {
        UUID userId = persistedUserId();

        Trip trip = fixture.createTripWithBrief(
                new CreateTripCommand(userId, "Japan in spring", Money.of("4000.00", "USD")));

        assertThat(trips.findByIdAndUserId(trip.id(), userId)).isPresent();
        assertThat(briefs.findByTripId(trip.id())).isPresent();
    }

    @Test
    void aCheckedExceptionRollsBackEveryTableTheUseCaseTouched() {
        UUID userId = persistedUserId();

        assertThatThrownBy(() -> fixture.createTripWithBriefThenFail(
                new CreateTripCommand(userId, "Doomed trip", Money.of("100.00", "USD"))))
                .isInstanceOf(FixtureFailure.class);

        // No partial write: neither the parent nor the child survived.
        assertThat(trips.findAllByUserId(userId)).isEmpty();
    }

    private UUID persistedUserId() {
        Instant now = Instant.now();
        return users.save(new User(UUID.randomUUID(), null,
                "user-" + UUID.randomUUID() + "@example.test", null, false, Role.USER, true, 0,
                null, now, now)).id();
    }
}
