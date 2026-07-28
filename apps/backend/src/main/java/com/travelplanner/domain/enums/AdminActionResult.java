package com.travelplanner.domain.enums;

/**
 * How an audited admin mutation ended (PLAN §4.0.6 "who, what, target user, when" plus the result
 * the task brief adds).
 *
 * <p>The audit row is written inside the same transaction as the change it describes, so a refused
 * or rolled-back action leaves no row at all — the table records what happened to the database, and
 * is never allowed to claim a change that did not commit. That is why there is no {@code REFUSED}
 * constant: a refusal is a security-log event, not a change.
 *
 * <p>{@link #PARTIAL} exists for the one outcome that does commit while still being wrong: the
 * account change succeeded but the ADR 009 §1 session revocation reported no account to revoke.
 * That combination means a disabled account may still hold a live token, which is precisely the
 * condition an operator must be able to find afterwards.
 */
public enum AdminActionResult {

    /** The change committed and every session it was required to terminate was terminated. */
    SUCCESS,

    /** The change committed, but its session revocation found no account to revoke. */
    PARTIAL
}
