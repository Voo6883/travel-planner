package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.account.AccountTestFakes;
import com.travelplanner.application.account.AccountTestFakes.CapturingMailer;
import com.travelplanner.application.auth.AuthTestFakes;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.application.mail.MailDispatcher;
import com.travelplanner.application.mail.MailTemplateRenderer;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Research-complete mail claim + preference gate (task 27, UC-N04). */
class ResearchCompletionMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private ResearchTestFakes.InMemoryJobs jobs;
    private ResearchTestFakes.InMemoryTrips trips;
    private FakeUsers users;
    private CapturingMailer mailer;
    private ResearchCompletionMailService service;

    @BeforeEach
    void setUp() {
        jobs = new ResearchTestFakes.InMemoryJobs();
        trips = new ResearchTestFakes.InMemoryTrips();
        users = new FakeUsers();
        mailer = new CapturingMailer();
        ResearchCompletionMailWriter writer = new ResearchCompletionMailWriter(
                jobs, trips, users, new MailTemplateRenderer(), new MailDispatcher(mailer),
                AccountTestFakes.mailProperties());
        service = new ResearchCompletionMailService(writer);
    }

    @Test
    void sendsOnceWhenEmailIsVerifiedAndClaimsTheStamp() {
        User user = AuthTestFakes.user("t@example.com", "traveller", "hash");
        users.save(user);
        Trip trip = Trip.create(user.id(), "Japan spring", NOW);
        trips.seed(trip);
        ResearchJob completed = ResearchJob.queue(trip.id(), user.id(), NOW)
                .markRunning(NOW).complete(NOW);
        jobs.seed(completed);

        service.onResearchReady(new ResearchReadyEvent(completed.id(), trip.id(), user.id()));

        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.only().subject()).containsIgnoringCase("research");
        assertThat(jobs.stored(completed.id()).completionMailSentAt()).isNotNull();

        service.onResearchReady(new ResearchReadyEvent(completed.id(), trip.id(), user.id()));
        assertThat(mailer.sent()).hasSize(1);
    }

    @Test
    void skipsUnverifiedAccountsWithoutClaiming() {
        User user = new User(UUID.randomUUID(), "traveller", "t@example.com", "hash",
                false, Role.USER, true, 0, null, null, NOW, NOW);
        users.save(user);
        Trip trip = Trip.create(user.id(), "Trip", NOW);
        trips.seed(trip);
        ResearchJob completed = ResearchJob.queue(trip.id(), user.id(), NOW)
                .markRunning(NOW).complete(NOW);
        jobs.seed(completed);

        service.onResearchReady(new ResearchReadyEvent(completed.id(), trip.id(), user.id()));

        assertThat(mailer.sent()).isEmpty();
        assertThat(jobs.stored(completed.id()).completionMailSentAt()).isNull();
    }
}
