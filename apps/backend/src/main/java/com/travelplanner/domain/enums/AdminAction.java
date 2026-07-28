package com.travelplanner.domain.enums;

/**
 * The complete set of account mutations an administrator can perform (PLAN §4.0.6).
 *
 * <p>An enum rather than a free string because the value is the audit key: {@code audit_event.action}
 * is what an investigation filters on, and two spellings of the same event make a trail that reads
 * as though nothing happened. A new admin capability adds a constant here, a row to the CHECK
 * constraint in V12, and a test — which is exactly the friction such a capability deserves.
 *
 * <p>Reads are absent on purpose. Listing and viewing accounts change nothing, and auditing every
 * page view would bury the four rows that matter under thousands that do not.
 */
public enum AdminAction {

    /** PLAN §4.0.6 — the account was switched off. Terminates every session (ADR 009 §1). */
    DISABLE_USER,

    /** PLAN §4.0.6 — the account was switched back on. Terminated sessions are not restored. */
    ENABLE_USER,

    /** UC-A16 — an administrator set a new password. Terminates every session (ADR 009 §1). */
    RESET_PASSWORD
}
