package com.travelplanner.application.research;

import com.travelplanner.application.mail.MailDispatcher;
import com.travelplanner.application.mail.MailTemplate;
import com.travelplanner.application.mail.MailTemplateRenderer;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.MailProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Short transactional writer for research-complete mail claims.
 *
 * <p>Separated from the {@code @TransactionalEventListener} so the listener is not also
 * {@code @Transactional} with default REQUIRED — Spring forbids that combination unless
 * propagation is {@code REQUIRES_NEW} or {@code NOT_SUPPORTED}.
 */
@Service
@RequiresDatabase
class ResearchCompletionMailWriter {

    private static final Logger log = LoggerFactory.getLogger(ResearchCompletionMailWriter.class);

    private final ResearchJobRepositoryPort jobs;
    private final TripRepositoryPort trips;
    private final UserRepositoryPort users;
    private final MailTemplateRenderer renderer;
    private final MailDispatcher dispatcher;
    private final MailProperties properties;

    ResearchCompletionMailWriter(
            ResearchJobRepositoryPort jobs,
            TripRepositoryPort trips,
            UserRepositoryPort users,
            MailTemplateRenderer renderer,
            MailDispatcher dispatcher,
            MailProperties properties) {
        this.jobs = jobs;
        this.trips = trips;
        this.users = users;
        this.renderer = renderer;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @TransactionalWrite
    void sendIfEligible(ResearchReadyEvent event) {
        ResearchJob job = jobs.findById(event.jobId()).orElse(null);
        if (job == null || !job.tripId().equals(event.tripId()) || !job.userId().equals(event.userId())) {
            return;
        }
        User user = users.findById(event.userId()).orElse(null);
        if (user == null || !user.emailVerified() || !user.enabled()) {
            log.info("research_complete_mail_skipped job={} reason=preference_or_unverified",
                    event.jobId());
            return;
        }
        ResearchJob claimed = job.claimCompletionMail(Instant.now()).orElse(null);
        if (claimed == null) {
            log.info("research_complete_mail_skipped job={} reason=already_sent", event.jobId());
            return;
        }
        jobs.save(claimed);
        Trip trip = trips.findByIdAndUserId(event.tripId(), event.userId()).orElse(null);
        String tripName = trip == null ? "your trip" : trip.name();
        String tripUrl = properties.getAppBaseUrl() + "/trips/" + event.tripId();
        dispatcher.dispatch(
                renderer.render(MailTemplate.RESEARCH_COMPLETE, user.email(), Map.of(
                        "trip_name", tripName,
                        "trip_url", tripUrl)),
                MailTemplate.RESEARCH_COMPLETE);
    }
}
