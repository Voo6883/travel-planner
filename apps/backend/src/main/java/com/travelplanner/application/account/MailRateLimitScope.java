package com.travelplanner.application.account;

/**
 * The mail actions ADR 009 §6 requires a rate limit on.
 *
 * <p>The key is persisted into {@code mail_rate_limit.scope} with an {@code :email} or {@code :ip}
 * suffix, and V9's CHECK constraint requires it to be lower-case. Keeping the string here rather
 * than at the call site means the two counters for one action can never be spelled differently and
 * silently stop being the same limit.
 */
public enum MailRateLimitScope {

    /** {@code POST /auth/password/forgot} (UC-A07). */
    PASSWORD_FORGOT("password_forgot"),

    /** {@code POST /auth/verify-email/resend} (UC-A13). */
    VERIFY_EMAIL_RESEND("verify_email_resend");

    private final String key;

    MailRateLimitScope(String key) {
        this.key = key;
    }

    /** The window keyed on the submitted address. */
    public String emailScope() {
        return key + ":email";
    }

    /** The window keyed on the caller's network address. */
    public String ipScope() {
        return key + ":ip";
    }
}
