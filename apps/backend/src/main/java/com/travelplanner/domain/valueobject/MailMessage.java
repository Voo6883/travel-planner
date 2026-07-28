package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.Objects;

/**
 * One transactional email, ready to hand to a provider (PLAN §4.0.10, which locks these four
 * fields).
 *
 * <p>Both bodies are required, not one or the other. A mail client that refuses HTML — or a screen
 * reader, or a plain-text-only corporate gateway — still has to be able to read a password-reset
 * link, and a provider that generates the text part by stripping tags produces something users are
 * routinely told not to trust. Rendering both from the same model is cheap; discovering that the
 * text alternative was empty is not.
 *
 * <p>Nothing here identifies a template. The audit event that records a send names the template
 * separately (see {@code application/mail/MailDispatcher}), so that logging a message can never
 * accidentally log its contents.
 *
 * <p>It lives in {@code domain/valueobject/} rather than beside {@link
 * com.travelplanner.domain.port.MailerPort} because Java requires a file per public type and this
 * is what it is: a value object with no identity. PLAN §4.0.10 shows the two in one snippet, which
 * is a listing convenience rather than a package decision.
 */
public record MailMessage(String to, String subject, String htmlBody, String textBody) {

    public MailMessage {
        to = requireText(to, "to");
        subject = requireText(subject, "subject");
        htmlBody = requireText(htmlBody, "html_body");
        textBody = requireText(textBody, "text_body");
    }

    /**
     * Redacts the recipient. {@code MailMessage} is the one object in the system that holds both an
     * address and the body of a message about that person's account, so an accidental
     * {@code log.debug("sending {}", message)} would publish exactly what PLAN §4.0.10's "no PII in
     * logs" rule forbids. Making the default rendering safe costs nothing and removes the trap.
     */
    @Override
    public String toString() {
        return "MailMessage[to=***, subject=" + subject + ", body=***]";
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(field, "field");
        if (value == null || value.isBlank()) {
            throw ValidationFailedException.field(field, "must not be blank");
        }
        return value;
    }
}
