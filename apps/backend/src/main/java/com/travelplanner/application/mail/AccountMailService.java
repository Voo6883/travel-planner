package com.travelplanner.application.mail;

import com.travelplanner.config.MailProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The semantic mail API the account-lifecycle services call: one method per thing that can happen
 * to an account, no template names or model maps at the call site.
 *
 * <p>Two rules hold across every method here.
 *
 * <p><strong>Raw tokens pass through and are never stored or logged.</strong> A token arrives as an
 * argument, goes into a link, and leaves in the message body. It is not a field, not returned, and
 * not part of any audit event — {@link MailDispatcher} logs a template key and a recipient hash and
 * nothing else (task 09 "do not log reset/verification tokens").
 *
 * <p><strong>Links point at the frontend, not the API.</strong> {@code /verify-email?token=…} is a
 * page (task 11) that calls {@code POST /auth/verify-email/confirm} on the user's behalf. Mailing
 * an API URL would show a person who clicked a link in their inbox a bare {@code 204} and no route
 * back into the product.
 */
@Service
public class AccountMailService {

    private final MailTemplateRenderer renderer;
    private final MailDispatcher dispatcher;
    private final MailProperties properties;

    public AccountMailService(MailTemplateRenderer renderer, MailDispatcher dispatcher,
            MailProperties properties) {
        this.renderer = renderer;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    /** UC-N01 — a new account was created. */
    public void sendWelcome(String email, String username) {
        send(MailTemplate.WELCOME, email, Map.of(
                "username", username == null ? email : username,
                "app_url", properties.getAppBaseUrl()));
    }

    /** UC-N02, UC-A08 — confirm this address. Also sent by the resend endpoint (UC-A13). */
    public void sendVerification(String email, String rawToken) {
        send(MailTemplate.VERIFY_EMAIL, email, Map.of(
                "verify_link", link("/verify-email", rawToken),
                "app_url", properties.getAppBaseUrl()));
    }

    /** UC-N03, UC-A07 — the one-hour single-use reset link. */
    public void sendPasswordReset(String email, String rawToken) {
        send(MailTemplate.RESET_PASSWORD, email, Map.of(
                "reset_link", link("/reset-password", rawToken),
                "app_url", properties.getAppBaseUrl()));
    }

    /**
     * Sent after a reset or a self-service change succeeds — the only channel that tells an account
     * owner their password was changed by somebody else, while it is still worth knowing.
     */
    public void sendPasswordChanged(String email) {
        send(MailTemplate.RESET_CONFIRMATION, email, Map.of(
                "app_url", properties.getAppBaseUrl(),
                "forgot_url", properties.getAppBaseUrl() + "/forgot-password"));
    }

    /**
     * Somebody submitted the sign-up form with an address that already has a local account.
     *
     * <p>This mail is the deferred half of ADR 009 §6's uniform {@code 202}: the HTTP response
     * cannot say "that account exists" without answering the same question for an attacker, but the
     * mailbox can, because only its owner reads it.
     */
    public void sendAccountAlreadyExists(String email) {
        send(MailTemplate.ACCOUNT_EXISTS, email, Map.of(
                "sign_in_url", properties.getAppBaseUrl() + "/sign-in",
                "forgot_url", properties.getAppBaseUrl() + "/forgot-password"));
    }

    /**
     * The address was free, but the chosen username was not — so no account was created and the
     * person is waiting for a verification mail that will never arrive.
     *
     * <p>Without this mail, a username collision is a dead end: the form says "check your email",
     * and nothing ever comes. That is the concrete usability defect task 08 recorded and this task
     * closes.
     */
    public void sendUsernameTaken(String email, String username) {
        send(MailTemplate.USERNAME_TAKEN, email, Map.of(
                "username", username == null ? "" : username,
                "sign_up_url", properties.getAppBaseUrl() + "/sign-up"));
    }

    /**
     * The account exists but has no local password — it signs in through a provider (ADR 009 §4).
     *
     * <p>Sent by forgot-password instead of a reset link, and by registration when the address
     * already belongs to a provider-only account. Minting a local password here would add a second
     * way into an account whose owner chose to have only one.
     */
    public void sendProviderSignIn(String email, List<String> providers) {
        send(MailTemplate.PROVIDER_SIGN_IN, email, Map.of(
                "providers", providers == null || providers.isEmpty()
                        ? "your identity provider" : String.join(" or ", providers),
                "sign_in_url", properties.getAppBaseUrl() + "/sign-in"));
    }

    private void send(MailTemplate template, String email, Map<String, String> model) {
        dispatcher.dispatch(renderer.render(template, email, model), template);
    }

    /**
     * The token is URL-encoded even though base64url is already URL-safe. The encoding is a property
     * of how the token happens to be generated today, and a link that breaks silently when that
     * changes is a link nobody can debug from a screenshot.
     */
    private String link(String path, String rawToken) {
        return properties.getAppBaseUrl() + path + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
