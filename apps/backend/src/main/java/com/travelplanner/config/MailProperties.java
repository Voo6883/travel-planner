package com.travelplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail delivery configuration (PLAN §4.0.10, ADR 004).
 *
 * <p>The defaults are the ones that make a fresh checkout work with no secrets at all:
 * {@code provider=stub} logs the mail instead of sending it. That is not a convenience — it is the
 * task's "do not make live Resend credentials mandatory in development or CI" rule expressed as a
 * default, so CI cannot accidentally start needing an API key.
 *
 * <p>{@code RESEND_API_KEY}, {@code MAIL_FROM}, and {@code MAILER_PROVIDER} arrive from the single
 * root {@code .env} (§4.0.0.2) and are never committed.
 */
@ConfigurationProperties(prefix = "travelplanner.mail")
public class MailProperties {

    /** The value that selects {@code StubMailerAdapter}; anything else must name a real provider. */
    public static final String STUB_PROVIDER = "stub";

    /** The value that selects {@code ResendMailerAdapter}. */
    public static final String RESEND_PROVIDER = "resend";

    private String provider = STUB_PROVIDER;

    /**
     * The {@code From:} address. A placeholder by default, because a real one has to be a domain
     * verified in Resend and there is no correct value to commit.
     */
    private String from = "Travel Planner <noreply@travelplanner.local>";

    /**
     * Where a mailed link points. This is the <em>frontend</em> origin, not the API's: the user
     * clicks through to a page (task 11), which then calls the confirm endpoint. Sending them
     * straight to the API would show them a bare 204 and no way back into the product.
     */
    private String appBaseUrl = "http://localhost:3000";

    private final Resend resend = new Resend();

    public String getProvider() {
        return provider;
    }

    /**
     * True when no real mail provider is configured.
     *
     * <p>Read by {@code RegistrationService} to decide whether a new account starts verified. With
     * the stub there is no mailbox to receive a link, so requiring verification would make sign-up
     * a dead end for anyone not reading the application log at {@code DEBUG}.
     *
     * <p>{@code MailConfigValidator} refuses to start under the {@code prod} profile in this state,
     * so the relaxation cannot reach production.
     */
    public boolean isStub() {
        return STUB_PROVIDER.equals(provider);
    }

    public void setProvider(String provider) {
        this.provider = provider == null || provider.isBlank() ? STUB_PROVIDER : provider.trim();
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        // Trailing slashes are stripped so that link building is a plain concatenation and cannot
        // produce `//verify-email`, which some routers treat as a different path.
        this.appBaseUrl = appBaseUrl == null ? "" : appBaseUrl.replaceAll("/+$", "");
    }

    public Resend getResend() {
        return resend;
    }

    /** Settings that exist only when {@code provider=resend}. */
    public static class Resend {

        private String apiKey = "";

        /** Overridable so a test can point the adapter at a local stub server instead of the internet. */
        private String baseUrl = "https://api.resend.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
