package com.travelplanner.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable of the authentication boundary, in one place, bound from configuration so that
 * cookie hardening and lockout thresholds are per-environment settings rather than constants
 * recompiled into a jar (12-Factor, PLAN §4.0.9).
 *
 * <p>The defaults are the values the ADRs lock, so an environment that overrides nothing is
 * already correct: 30-minute access token and 14-day rotating refresh token (ADR 009 §3), BCrypt
 * at strength 12 with an 8-character minimum (PLAN §4.0.9 requires 10+ and 8), and 5 failures per
 * 15 minutes (ADR 009 §6).
 *
 * <p>{@code secure} is the one default that is deliberately <em>not</em> the hardened value.
 * {@code Secure} cookies are dropped by the browser over plain HTTP, and the locked local
 * experience is {@code docker compose up} on {@code http://localhost} (ADR 006). It is switched on
 * in {@code application-prod.yml}, where TLS is guaranteed.
 */
@ConfigurationProperties(prefix = "travelplanner.security")
public class AuthSecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Session session = new Session();
    private final Password password = new Password();
    private final Lockout lockout = new Lockout();

    public Jwt getJwt() {
        return jwt;
    }

    public Session getSession() {
        return session;
    }

    public Password getPassword() {
        return password;
    }

    public Lockout getLockout() {
        return lockout;
    }

    /** Access-token signing. The secret arrives as {@code JWT_SECRET} and is never committed. */
    public static class Jwt {

        /** HS256 needs at least 256 bits of key; anything shorter is rejected at startup. */
        public static final int MINIMUM_SECRET_LENGTH = 32;

        private String secret = "";
        private String issuer = "travel-planner";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret == null ? "" : secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    /** Cookie transport (ADR 002 and ADR 006). */
    public static class Session {

        private String cookieName = "tp_session";
        private String refreshCookieName = "tp_refresh";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration refreshTokenTtl = Duration.ofDays(14);
        private boolean secure;
        private String sameSite = "Lax";
        /**
         * The refresh cookie is scoped to the auth routes, so it is not attached to every ordinary
         * API call. ADR 006 puts the browser on the frontend origin behind a pass-through rewrite,
         * so the path the browser sees is the same one the backend serves.
         */
        private String refreshCookiePath = "/api/v1/auth";

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getRefreshCookieName() {
            return refreshCookieName;
        }

        public void setRefreshCookieName(String refreshCookieName) {
            this.refreshCookieName = refreshCookieName;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public String getRefreshCookiePath() {
            return refreshCookiePath;
        }

        public void setRefreshCookiePath(String refreshCookiePath) {
            this.refreshCookiePath = refreshCookiePath;
        }
    }

    /** PLAN §4.0.9: BCrypt strength 10+, minimum length 8. */
    public static class Password {

        private int bcryptStrength = 12;
        private int minLength = 8;
        /**
         * BCrypt hashes the first 72 <em>bytes</em> and silently ignores the rest, so a longer
         * password is not stronger — it is a pair of passwords that authenticate each other.
         * Rejecting the input is honest; truncating it is not.
         */
        private int maxLength = 72;

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }
    }

    /** ADR 009 §6: 5 failures / 15 min, keyed on identifier <em>and</em> client address. */
    public static class Lockout {

        private int maxFailures = 5;
        private Duration window = Duration.ofMinutes(15);

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
