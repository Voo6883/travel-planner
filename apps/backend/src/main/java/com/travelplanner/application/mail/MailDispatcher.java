package com.travelplanner.application.mail;

import com.travelplanner.domain.port.MailerPort;
import com.travelplanner.domain.valueobject.MailMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The only place {@link MailerPort} is called from, and the reason nothing else may call it.
 *
 * <h2>Sending happens after the commit</h2>
 *
 * <p>{@code AGENTS.md} forbids external HTTP inside a {@code @Transactional} method. Mail is
 * external HTTP, and three separate things go wrong if it is sent inline:
 *
 * <ul>
 *   <li>a pooled database connection is held open for the length of a network round trip;
 *   <li>{@code @TransactionalWrite} retries a deadlocked transaction up to three times, so a mail
 *       sent inside one can be sent three times;
 *   <li>worst of all, the transaction can still roll back <em>after</em> the mail is away — telling
 *       a user their password was reset when it was not, or mailing a verification link for an
 *       account that no longer exists.
 * </ul>
 *
 * <p>So a dispatch made inside a transaction is deferred to {@code afterCommit}. If the transaction
 * rolls back, {@code afterCommit} never runs and no mail is sent — which is exactly right: the
 * event being announced did not happen. Outside a transaction the send is immediate, because there
 * is nothing to wait for.
 *
 * <h2>A failed send never corrupts state</h2>
 *
 * <p>{@code afterCommit} runs after the database work is committed and unchangeable, and Spring
 * propagates an exception thrown there to the caller of {@code commit()}. A provider outage would
 * therefore surface as a 500 on a request whose work had already succeeded — the account is created,
 * the password is changed, and the user is told it failed. So the send is contained here: failures
 * are logged as an audit event and swallowed. The task's rule is "retry/failure handling that does
 * not leave invalid account state"; the state is already valid, and the only honest thing left to do
 * is record that the mail did not go out. The user's recourse — "resend verification", "forgot
 * password again" — is a first-class endpoint precisely because mail is unreliable.
 *
 * <h2>What the audit event contains</h2>
 *
 * <p>PLAN §4.0.10: "no PII in logs — log {@code mail_sent} with recipient hash or user id only".
 * The event carries the template key and a truncated SHA-256 of the lower-cased address, which is
 * enough to correlate "did this person get their mail?" across log lines without writing the address
 * itself. It never carries a subject, a body, or a token.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    /**
     * Enough hex to make an accidental collision irrelevant for correlation, and far too little to
     * reverse by dictionary — a full digest of a known address is trivially searchable, so a short
     * prefix is the safer artefact to keep in a log that may be shipped off-host.
     */
    private static final int RECIPIENT_HASH_CHARS = 12;

    private final MailerPort mailer;

    public MailDispatcher(MailerPort mailer) {
        this.mailer = mailer;
    }

    /**
     * Sends after the current transaction commits, or immediately when there is none.
     *
     * @param template named separately from the message so that recording a send can never
     *        accidentally record its contents — {@link MailMessage} has no template field
     */
    public void dispatch(MailMessage message, MailTemplate template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(message, template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(message, template);
            }
        });
    }

    private void send(MailMessage message, MailTemplate template) {
        String recipientHash = recipientHash(message.to());
        try {
            mailer.send(message);
            log.info("mail_sent template={} recipient_hash={}", template.key(), recipientHash);
        } catch (RuntimeException failed) {
            // Deliberately not rethrown — see the class javadoc. WARN rather than ERROR: the
            // account change succeeded, and the user has a self-service way to ask again.
            log.warn("mail_failed template={} recipient_hash={} reason={}",
                    template.key(), recipientHash, failed.getClass().getSimpleName(), failed);
        }
    }

    /** Truncated SHA-256 of the lower-cased address. Never the address (PLAN §4.0.10). */
    static String recipientHash(String recipient) {
        String normalised = recipient == null ? "" : recipient.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of()
                    .formatHex(digest.digest(normalised.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, RECIPIENT_HASH_CHARS);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
