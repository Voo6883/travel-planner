package com.travelplanner.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token lifetimes and mail rate limits for the account-lifecycle flows (§4.0.10, ADR 009 §6).
 *
 * <p>Bound from configuration rather than compiled in, for the same reason
 * {@link AuthSecurityProperties} is: these are per-environment security settings, and a test that
 * needs a one-second expiry should not have to wait an hour to prove the expiry works.
 */
@ConfigurationProperties(prefix = "travelplanner.account")
public class AccountLifecycleProperties {

    private final Tokens tokens = new Tokens();
    private final MailRateLimit mailRateLimit = new MailRateLimit();

    public Tokens getTokens() {
        return tokens;
    }

    public MailRateLimit getMailRateLimit() {
        return mailRateLimit;
    }

    /** How long a mailed link stays redeemable. */
    public static class Tokens {

        /**
         * Generous, because the cost of expiry here is a person who cannot use the product at all
         * until they ask for another mail — and mail is regularly delayed by hours in corporate
         * filtering. A verification link grants no power over an existing account: the worst it can
         * do is confirm an address the holder already controls.
         */
        private Duration verificationTtl = Duration.ofHours(24);

        /**
         * One hour, locked by ADR 004 ("single-use tokens, 1h expiry, hashed at rest"). A reset link
         * <em>does</em> grant control of an account, so its window is the shortest one that still
         * survives ordinary mail latency.
         */
        private Duration resetTtl = Duration.ofHours(1);

        public Duration getVerificationTtl() {
            return verificationTtl;
        }

        public void setVerificationTtl(Duration verificationTtl) {
            this.verificationTtl = verificationTtl;
        }

        public Duration getResetTtl() {
            return resetTtl;
        }

        public void setResetTtl(Duration resetTtl) {
            this.resetTtl = resetTtl;
        }
    }

    /**
     * ADR 009 §6 — {@code /auth/password/forgot} and {@code /auth/verify-email/resend} are limited
     * per email <em>and</em> per client address.
     *
     * <p>Two limits, because either alone has a hole. Per-email only lets one machine walk a list of
     * addresses at full speed; per-IP only lets a botnet flood one victim's mailbox from a thousand
     * hosts. The per-IP ceiling is the looser of the two because a university, an office, or a
     * mobile carrier NAT puts many legitimate users behind one address.
     */
    public static class MailRateLimit {

        private Duration window = Duration.ofHours(1);
        private int maxPerEmail = 3;
        private int maxPerIp = 10;

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getMaxPerEmail() {
            return maxPerEmail;
        }

        public void setMaxPerEmail(int maxPerEmail) {
            this.maxPerEmail = maxPerEmail;
        }

        public int getMaxPerIp() {
            return maxPerIp;
        }

        public void setMaxPerIp(int maxPerIp) {
            this.maxPerIp = maxPerIp;
        }
    }
}
