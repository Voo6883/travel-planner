package com.travelplanner.infrastructure.mail;

import com.travelplanner.config.MailProperties;
import com.travelplanner.domain.port.MailerPort;
import com.travelplanner.domain.valueobject.MailMessage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The live provider (PLAN §4.0.10, ADR 004). <strong>The only class in the system that knows Resend
 * exists</strong> — every caller speaks {@link MailerPort}, so replacing the vendor is a new file in
 * this package and one configuration value.
 *
 * <h2>It cannot exist unless it is asked for</h2>
 *
 * <p>The bean is conditional on {@code travelplanner.mail.provider=resend}. A developer machine, the
 * unit suite, the Testcontainers suite, and CI all run with the stub, so no test can reach the
 * network by accident and no build can start requiring a secret. When the property <em>is</em> set,
 * a missing API key fails at construction rather than at the first password reset — a mailer that
 * silently cannot send is worse than one that refuses to start.
 *
 * <h2>Retry</h2>
 *
 * <p>Three attempts with exponential backoff on transport failure, matching PLAN §4.0.2-E2's
 * template. Retrying is safe here because {@link com.travelplanner.application.mail.MailDispatcher}
 * runs this after the transaction has committed: the worst case of a retry that actually succeeded
 * the first time is a duplicate mail, never a duplicate account change. When every attempt fails the
 * dispatcher records {@code mail_failed} and the account state — already committed — is untouched.
 *
 * <h2>No plain HTTP client is used directly</h2>
 *
 * <p>{@code RestClient} comes from {@code spring-boot-starter-web}, which is already a dependency.
 * The Resend SDK would add a transitive tree to serialise one JSON object with five fields.
 */
@Component
@ConditionalOnProperty(name = "travelplanner.mail.provider",
        havingValue = MailProperties.RESEND_PROVIDER)
public class ResendMailerAdapter implements MailerPort {

    private static final Logger log = LoggerFactory.getLogger(ResendMailerAdapter.class);

    private static final String SEND_PATH = "/emails";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /**
     * Bounded, because this runs on the request thread's {@code afterCommit} callback. An unbounded
     * read would let a hung provider hold a servlet thread until the container's own timeout.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String from;

    public ResendMailerAdapter(MailProperties properties, RestClient.Builder builder) {
        MailProperties.Resend resend = properties.getResend();
        if (resend.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "travelplanner.mail.provider=resend but RESEND_API_KEY is not set. "
                            + "Set the key, or use the stub provider.");
        }
        this.from = properties.getFrom();
        this.client = builder
                .baseUrl(resend.getBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resend.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("Resend mailer active");
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500L, multiplier = 2.0))
    public void send(MailMessage message) {
        client.post()
                .uri(SEND_PATH)
                .body(payloadFor(message))
                .retrieve()
                // The response body carries a provider message id. Discarded deliberately: no caller
                // can act on it, and holding it would invite somebody to log it beside a recipient.
                .toBodilessEntity();
    }

    /**
     * Resend's documented request shape. A {@code Map} rather than a DTO because this is one
     * vendor-specific payload built in one place; a record would add a type whose only job is to be
     * serialised immediately.
     */
    private Map<String, Object> payloadFor(MailMessage message) {
        return Map.of(
                "from", from,
                "to", List.of(message.to()),
                "subject", message.subject(),
                "html", message.htmlBody(),
                "text", message.textBody());
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
