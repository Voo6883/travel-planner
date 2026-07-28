package com.travelplanner.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.MailProperties;
import com.travelplanner.domain.valueobject.MailMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The default provider (§4.0.7, §4.0.10), and the guard that keeps the live one out of CI. */
class StubMailerAdapterTest {

    private final StubMailerAdapter mailer = new StubMailerAdapter();

    @Test
    void keepsWhatItWasGivenSoATestCanFollowALink() {
        mailer.send(message("Confirm your email address"));

        assertThat(mailer.lastMessage()).hasValueSatisfying(sent -> {
            assertThat(sent.subject()).isEqualTo("Confirm your email address");
            assertThat(sent.to()).isEqualTo("aisyah@example.com");
            assertThat(sent.textBody()).contains("token=abc");
        });
    }

    @Test
    void reportsTheOutboxMostRecentFirst() {
        mailer.send(message("first"));
        mailer.send(message("second"));

        assertThat(mailer.outbox()).extracting(MailMessage::subject)
                .containsExactly("second", "first");
    }

    @Test
    void boundsTheOutboxSoALongRunningLocalProcessDoesNotLeak() {
        for (int index = 0; index < StubMailerAdapter.OUTBOX_CAPACITY + 10; index++) {
            mailer.send(message("subject " + index));
        }

        assertThat(mailer.outbox()).hasSize(StubMailerAdapter.OUTBOX_CAPACITY);
    }

    @Test
    void clearsOnRequest() {
        mailer.send(message("first"));
        mailer.clear();

        assertThat(mailer.outbox()).isEmpty();
        assertThat(mailer.lastMessage()).isEmpty();
    }

    @Test
    void theLiveAdapterRefusesToStartWithoutAnApiKey() {
        // The bean is only created when travelplanner.mail.provider=resend, and when it is, a
        // missing key must fail at startup rather than at the first password reset — a mailer that
        // silently cannot send is worse than one that refuses to run.
        MailProperties properties = new MailProperties();
        properties.setProvider(MailProperties.RESEND_PROVIDER);

        assertThatThrownBy(() -> new ResendMailerAdapter(properties, RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    private static MailMessage message(String subject) {
        return new MailMessage("aisyah@example.com", subject, "<p>hi</p>",
                "https://app.example.test/verify-email?token=abc");
    }
}
