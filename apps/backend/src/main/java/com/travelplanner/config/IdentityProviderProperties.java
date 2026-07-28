package com.travelplanner.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External identity provider configuration (ADR 004 "Env vars", PLAN §4.0.5).
 *
 * <p><strong>Every credential defaults to empty, and that is the feature.</strong> A fresh checkout,
 * the whole test suite, and CI all run with no Firebase project and no GitHub OAuth app — task 10's
 * "do not rely on a live provider in CI" expressed as a default rather than as a note in a README.
 * {@link IdentityProviderConfig} reads {@code isConfigured()} to decide between the real adapter and
 * the development one, and the development one refuses to exist under the {@code prod} profile.
 *
 * <p>The endpoint URLs are settable for the same reason {@code MailProperties.Resend.baseUrl} is: a
 * test points them at a local stub server instead of at the internet.
 */
@ConfigurationProperties(prefix = "travelplanner.identity")
public class IdentityProviderProperties {

    /**
     * Where an OAuth round trip returns the browser. The <em>frontend</em> origin, not the API's:
     * the callback is the last thing the user's browser does inside our API, and leaving them on a
     * bare JSON endpoint would end the flow with no route back into the product.
     */
    private String appBaseUrl = "http://localhost:3000";

    /** Where a completed sign-in lands (PLAN §4.0.5: "Set JWT httpOnly cookie → redirect to /trips"). */
    private String successPath = "/trips";

    /** Where a failed round trip lands, with {@code ?error=<registered code>} appended. */
    private String failurePath = "/sign-in";

    private final Firebase firebase = new Firebase();
    private final Github github = new Github();

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        // Trailing slashes stripped so link building stays a plain concatenation and cannot produce
        // `//trips`, which some routers treat as a different path.
        this.appBaseUrl = appBaseUrl == null ? "" : appBaseUrl.replaceAll("/+$", "");
    }

    public String getSuccessPath() {
        return successPath;
    }

    public void setSuccessPath(String successPath) {
        this.successPath = successPath;
    }

    public String getFailurePath() {
        return failurePath;
    }

    public void setFailurePath(String failurePath) {
        this.failurePath = failurePath;
    }

    public Firebase getFirebase() {
        return firebase;
    }

    public Github getGithub() {
        return github;
    }

    /** Google sign-in through Firebase Authentication (ADR 004). */
    public static class Firebase {

        private String projectId = "";

        /**
         * Google's JWKS for the {@code securetoken} service account — the public half of the keys
         * every Firebase ID token is signed with. Fetching it is what makes verification possible
         * without a service-account credential of our own: verifying a token needs Google's public
         * key, not our private one.
         */
        private String jwksUri =
                "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

        /** The {@code iss} claim is this plus the project id. */
        private String issuerPrefix = "https://securetoken.google.com/";

        /**
         * The only accepted value of {@code firebase.sign_in_provider} (ADR 009 §4). Configurable
         * so that adding a second Firebase provider is a deliberate configuration change with a
         * reviewer, rather than something that starts working the moment somebody clicks a toggle
         * in the Firebase console.
         */
        private String requiredSignInProvider = "google.com";

        public boolean isConfigured() {
            return !projectId.isBlank();
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId == null ? "" : projectId.trim();
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getIssuerPrefix() {
            return issuerPrefix;
        }

        public void setIssuerPrefix(String issuerPrefix) {
            this.issuerPrefix = issuerPrefix;
        }

        public String getRequiredSignInProvider() {
            return requiredSignInProvider;
        }

        public void setRequiredSignInProvider(String requiredSignInProvider) {
            this.requiredSignInProvider = requiredSignInProvider;
        }

        /** {@code iss} the token must carry, derived rather than configured twice. */
        public String issuer() {
            return issuerPrefix + projectId;
        }
    }

    /** GitHub OAuth (ADR 004). The client secret never leaves the server. */
    public static class Github {

        private String clientId = "";
        private String clientSecret = "";
        private String callbackUrl = "http://localhost:8080/api/v1/auth/oauth/github/callback";
        private String authorizeUri = "https://github.com/login/oauth/authorize";
        private String tokenUri = "https://github.com/login/oauth/access_token";
        private String apiBaseUrl = "https://api.github.com";

        /**
         * {@code user:email} and nothing more. The primary verified address is the only thing this
         * system needs from GitHub, and a scope granted is a scope that has to be justified to every
         * user who reads the consent screen.
         */
        private String scope = "user:email";

        /** How long the {@code state} cookie lives. One redirect, not one session. */
        private Duration stateTtl = Duration.ofMinutes(10);

        /** Refuses to answer after this long, so a hung provider cannot hold a request thread. */
        private Duration requestTimeout = Duration.ofSeconds(10);

        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId == null ? "" : clientId.trim();
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public void setCallbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
        }

        public String getAuthorizeUri() {
            return authorizeUri;
        }

        public void setAuthorizeUri(String authorizeUri) {
            this.authorizeUri = authorizeUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Duration getStateTtl() {
            return stateTtl;
        }

        public void setStateTtl(Duration stateTtl) {
            this.stateTtl = stateTtl;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        /**
         * Deliberately overridden. The default would print the client secret into any log line that
         * dumped the properties object — the same reason {@code ProviderCredential} overrides it.
         */
        @Override
        public String toString() {
            return "Github[clientId=" + clientId + ", clientSecret=***, callbackUrl=" + callbackUrl + "]";
        }
    }
}
