package com.travelplanner.application.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.travelplanner.domain.port.MailerPort;
import com.travelplanner.domain.valueobject.MailMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The rule {@code AGENTS.md} states as "no LLM or HTTP inside a {@code @Transactional} method", and
 * what it protects: a mail must never be sent for a database change that has not committed, and a
 * provider outage must never undo a change that has.
 *
 * <p>The transaction is simulated through {@link TransactionSynchronizationManager} rather than by
 * starting a real one, which is what keeps this in the Docker-free unit suite. The dispatcher's only
 * interaction with Spring's transaction machinery is registering a synchronization, and that is
 * exactly what these tests drive.
 */
class MailDispatcherTest {

    private final RecordingMailer mailer = new RecordingMailer();
    private final MailDispatcher dispatcher = new MailDispatcher(mailer);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsImmediatelyWhenThereIsNoTransactionToWaitFor() {
        dispatcher.dispatch(message(), MailTemplate.WELCOME);

        assertThat(mailer.sent).hasSize(1);
    }

    @Test
    void withholdsTheMailUntilTheSurroundingTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(message(), MailTemplate.VERIFY_EMAIL);

        // Still pending: the account row it announces is not durable yet.
        assertThat(mailer.sent).isEmpty();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        assertThat(mailer.sent).hasSize(1);
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(message(), MailTemplate.RESET_PASSWORD);
        // A rollback simply never calls afterCommit, which is the whole mechanism: no mail claiming
        // a password was reset when it was not.
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void containsAProviderFailureSoACommittedAccountChangeIsNotReportedAsAnError() {
        mailer.failure = new IllegalStateException("provider unreachable");

        // afterCommit runs after the database work is unchangeable, and Spring propagates an
        // exception thrown there to the caller of commit(). Rethrowing would tell a user their
        // password change failed when it had already succeeded.
        assertThatCode(() -> dispatcher.dispatch(message(), MailTemplate.RESET_CONFIRMATION))
                .doesNotThrowAnyException();
    }

    @Test
    void hashesTheRecipientRatherThanNamingThemInTheAuditEvent() {
        // PLAN §4.0.10: "no PII in logs — log mail_sent with recipient hash or user id only".
        String hash = MailDispatcher.recipientHash("Aisyah@Example.com");

        assertThat(hash).doesNotContain("aisyah").doesNotContain("@").hasSize(12);
        // Case and surrounding space must not produce two different hashes for one address.
        assertThat(hash).isEqualTo(MailDispatcher.recipientHash("  aisyah@example.com "));
        assertThat(hash).isNotEqualTo(MailDispatcher.recipientHash("someone@example.com"));
    }

    private static MailMessage message() {
        return new MailMessage("aisyah@example.com", "Subject", "<p>html</p>", "text");
    }

    private static final class RecordingMailer implements MailerPort {

        private final List<MailMessage> sent = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(MailMessage message) {
            if (failure != null) {
                throw failure;
            }
            sent.add(message);
        }
    }
}
