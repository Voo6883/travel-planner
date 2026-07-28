package com.travelplanner.application.auth;

/**
 * Why every session for an account was terminated. Exactly the list ADR 009 §1 requires a
 * {@code token_version} bump for, plus the reuse case ADR 009 §3 adds.
 *
 * <p>The value is not persisted. It exists so the security event in the log says <em>why</em> a
 * user was signed out everywhere, which is the difference between an incident that can be
 * explained and one that cannot.
 *
 * <p>Tasks 09, 10, and 12 own the endpoints that raise most of these; task 08 owns the mechanism
 * and raises {@link #LOGOUT_ALL} and {@link #REFRESH_TOKEN_REUSE}. Adding an endpoint means
 * calling {@link SessionRevocationService}, never re-implementing the bump.
 */
public enum SessionRevocationReason {

    /** UC-A12 — the user changed their own password (task 09). */
    PASSWORD_CHANGED,

    /** UC-A16 — an administrator reset the password (task 12). */
    ADMIN_PASSWORD_RESET,

    /** PLAN §4.0.6 — an administrator disabled the account (task 12). */
    ADMIN_DISABLED,

    /** UC-A14 — the account was soft-deleted (task 09). */
    ACCOUNT_DELETED,

    /** ADR 009 §4 — a provider was unlinked (task 10). */
    PROVIDER_UNLINKED,

    /** UC-A10 extended — {@code POST /auth/logout-all}. */
    LOGOUT_ALL,

    /** ADR 009 §3 — a rotated refresh token was presented again; the family is revoked. */
    REFRESH_TOKEN_REUSE
}
