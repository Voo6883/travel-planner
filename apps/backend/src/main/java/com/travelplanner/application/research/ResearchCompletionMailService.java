package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
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
 * <p>{@link com.travelplanner.domain.model.ResearchJob#claimCompletionMail} stamps
 * {@code completion_mail_sent_at}. A second listener that loses the race sees an empty claim and
 * sends nothing. The claim write runs in {@link ResearchCompletionMailWriter} under
 * {@code @TransactionalWrite}; this listener stays non-transactional so Spring accepts the
 * {@code AFTER_COMMIT} binding.
 */
@Service
@RequiresDatabase
public class ResearchCompletionMailService {

    private final ResearchCompletionMailWriter writer;

    public ResearchCompletionMailService(ResearchCompletionMailWriter writer) {
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResearchReady(ResearchReadyEvent event) {
        writer.sendIfEligible(event);
    }
}
