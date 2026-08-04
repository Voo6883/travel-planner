package com.travelplanner.application.mail;

/**
 * The transactional mails this system sends (PLAN §4.0.10's table, plus the three ADR 009 §6
 * requires to keep the uniform responses usable).
 *
 * <p>An enum rather than a string, because the template name is also the audit key: every send is
 * logged as {@code mail_sent template=<key>}, and a typo in a string literal would produce an audit
 * trail with two spellings of the same event and a missing file at runtime.
 *
 * <p>Each constant names a pair of files on the classpath — {@code mail/templates/<key>.html} and
 * {@code .txt} — and carries its own subject. Subjects are English-only in v1; the frontend's
 * {@code en}/{@code ms} split has no server-side counterpart because nothing records a user's
 * preferred locale yet (reported as a follow-up rather than guessed at here).
 *
 * <h2>Why three of these exist</h2>
 *
 * <p>Task 08 made registration return an identical {@code 202} whether the address was free, taken,
 * or the username was taken (ADR 009 §6). That closes the enumeration channel but leaves the real
 * user with no way to find out what happened. {@link #ACCOUNT_EXISTS}, {@link #USERNAME_TAKEN}, and
 * {@link #PROVIDER_SIGN_IN} are the deferred half of that decision: the answer goes to the mailbox,
 * which only the address owner can read.
 */
public enum MailTemplate {

    /** UC-N01 — after a local sign-up, and after a first-time provider sign-up (task 10). */
    WELCOME("welcome", "Welcome to Travel Planner"),

    /** UC-N02, UC-A08 — the link that flips {@code email_verified}. */
    VERIFY_EMAIL("verify-email", "Confirm your email address"),

    /** UC-N03, UC-A07 — the one-hour, single-use reset link. */
    RESET_PASSWORD("reset-password", "Reset your Travel Planner password"),

    /**
     * Sent after a password reset or change succeeds. Not a courtesy: it is the only way the account
     * owner finds out that somebody else reset their password, and the window in which that is
     * still worth knowing is short.
     */
    RESET_CONFIRMATION("reset-confirmation", "Your Travel Planner password was changed"),

    /** UC-N04 / UC-C2-08 — research finished; stamped once per job (task 27). */
    RESEARCH_COMPLETE("research-complete", "Your travel research is ready"),

    /**
     * Someone tried to register an address that already has a local account. Tells the owner to sign
     * in or reset instead — the information the uniform {@code 202} deliberately withholds from the
     * HTTP response.
     */
    ACCOUNT_EXISTS("account-exists", "About your Travel Planner sign-up"),

    /**
     * The address was free but the username was not. The same subject line as
     * {@link #ACCOUNT_EXISTS} on purpose: the two mails go to different people in different
     * situations, and there is no reason for the subject alone to distinguish them.
     */
    USERNAME_TAKEN("username-taken", "About your Travel Planner sign-up"),

    /**
     * The account exists but has no local password (ADR 009 §4) — it signs in through Google or
     * GitHub. Sent instead of a reset link, because minting a local password for an account that
     * never had one would turn "I know your email address" into a second way in.
     */
    PROVIDER_SIGN_IN("provider-sign-in", "About your Travel Planner account");

    private final String key;
    private final String subject;

    MailTemplate(String key, String subject) {
        this.key = key;
        this.subject = subject;
    }

    /** File stem under {@code mail/templates/}, and the value logged in the audit event. */
    public String key() {
        return key;
    }

    public String subject() {
        return subject;
    }
}
