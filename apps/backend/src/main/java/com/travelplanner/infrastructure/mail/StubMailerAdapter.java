package com.travelplanner.infrastructure.mail;

import com.travelplanner.config.MailProperties;
import com.travelplanner.domain.port.MailerPort;
import com.travelplanner.domain.valueobject.MailMessage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default mailer (§4.0.7 stub-adapter rule, §4.0.10 {@code mailer.provider=stub}).
 *
 * <p><strong>It is the default, not the fallback.</strong> A fresh checkout, the whole test suite,
 * and CI all run with no {@code RESEND_API_KEY}, which is task 09's "do not make live Resend
 * credentials mandatory in development or CI" expressed as configuration rather than as a note in a
 * README.
 *
 * <h2>What it logs, and what it does not</h2>
 *
 * <p>At {@code INFO} it records only what {@link com.travelplanner.application.mail.MailDispatcher}
 * would: that a mail was handled, and for whom, as a hash. At {@code DEBUG} — which no profile
 * enables by default outside local development — it prints the full body <em>including any link</em>,
 * because in local development that is the only way to click a verification link at all.
 *
 * <p>That {@code DEBUG} case is the one deliberate exception to "do not log tokens", and it is
 * confined to the stub: the class cannot exist when {@code provider=resend}, so no production
 * configuration can reach this code path however the log level is set.
 *
 * <h2>The in-memory outbox</h2>
 *
 * <p>Kept so that a test can assert what was sent — subject, recipient, and that a link is present —
 * without a network, a mail server, or a fake SMTP container. It is bounded, because an outbox that
 * grows for the life of a long-running local process is a memory leak with a helpful name.
 */
@Component
@ConditionalOnProperty(name = "travelplanner.mail.provider", havingValue = MailProperties.STUB_PROVIDER,
        matchIfMissing = true)
public class StubMailerAdapter implements MailerPort {

    private static final Logger log = LoggerFactory.getLogger(StubMailerAdapter.class);

    /** Enough to inspect a whole sign-up flow; small enough never to matter. */
    static final int OUTBOX_CAPACITY = 50;

    private final Deque<MailMessage> outbox = new ArrayDeque<>();

    @Override
    public void send(MailMessage message) {
        record(message);
        log.info("Stub mailer accepted a message — subject='{}'", message.subject());
        // The body carries verification and reset links. Only reachable in the stub, and only when
        // somebody has deliberately turned on DEBUG for this package.
        log.debug("Stub mailer body for '{}':\n{}", message.subject(), message.textBody());
    }

    /** Most recent first. */
    public synchronized List<MailMessage> outbox() {
        return List.copyOf(outbox);
    }

    public synchronized Optional<MailMessage> lastMessage() {
        return Optional.ofNullable(outbox.peekFirst());
    }

    public synchronized void clear() {
        outbox.clear();
    }

    private synchronized void record(MailMessage message) {
        outbox.addFirst(message);
        while (outbox.size() > OUTBOX_CAPACITY) {
            outbox.removeLast();
        }
    }
}
