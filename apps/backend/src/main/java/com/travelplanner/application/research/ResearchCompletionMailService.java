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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Research-complete mail (task 27, UC-C2-08 / UC-N04, BACKLOG S4-8).
 *
 * <h2>Preference / offline policy (v1)</h2>
 *
 * <p>No presence channel or notification-preference column exists yet. v1 policy: send when the
 * owner has a verified, enabled email; skip otherwise. Async completion itself is the "offline"
 * case — the traveller may have closed the tab. A future preference column can gate this without
 * changing the claim stamp.
 *
 * <h2>Idempotency</h2>
 *
 * <p>{@link ResearchJob#claimCompletionMail} stamps {@code completion_mail_sent_at}. A second
 * listener that loses the race sees an empty claim and sends nothing. Mail HTTP runs via
 * {@link MailDispatcher} after this short write commits.
 */
@Service
@RequiresDatabase
public class ResearchCompletionMailService {

    private static final Logger log = LoggerFactory.getLogger(ResearchCompletionMailService.class);

    private final ResearchJobRepositoryPort jobs;
    private final TripRepositoryPort trips;
    private final UserRepositoryPort users;
    private final MailTemplateRenderer renderer;
    private final MailDispatcher dispatcher;
    private final MailProperties properties;

    public ResearchCompletionMailService(
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @TransactionalWrite
    public void onResearchReady(ResearchReadyEvent event) {
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
