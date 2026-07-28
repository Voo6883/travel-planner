package com.travelplanner.domain.port;

import com.travelplanner.domain.valueobject.MailMessage;

/**
 * Transactional mail delivery (PLAN §4.0.10, ADR 004). Implemented in
 * {@code infrastructure/mail/} — {@code StubMailerAdapter} by default,
 * {@code ResendMailerAdapter} when {@code travelplanner.mail.provider=resend}.
 *
 * <p><strong>Sending is external HTTP.</strong> {@code AGENTS.md} forbids that inside a
 * {@code @Transactional} method, and the rule has teeth here rather than being stylistic: a send
 * that takes two seconds holds a pooled database connection for two seconds, and a transaction that
 * is retried after a deadlock would send the mail twice. Application code therefore never calls
 * this port directly — it goes through {@code application/mail/MailDispatcher}, which defers the
 * send until after the surrounding transaction commits.
 *
 * <p>One method, and it returns nothing. A provider's message id is of no use to any caller in v1,
 * and returning one would invite a caller to wait for it — which is the blocking behaviour the
 * dispatcher exists to avoid.
 *
 * <p>Implementations may throw. The dispatcher contains the failure: a mail that could not be sent
 * must never roll back or corrupt the account change it was announcing (task 09, "retry/failure
 * handling that does not leave invalid account state").
 */
public interface MailerPort {

    /** @throws RuntimeException when the provider rejects or cannot be reached */
    void send(MailMessage message);
}
