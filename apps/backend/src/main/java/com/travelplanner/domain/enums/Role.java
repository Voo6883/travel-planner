package com.travelplanner.domain.enums;

/**
 * The complete authorisation vocabulary.
 *
 * <p>Two values, permanently. {@code docs/AGENT-HARNESS.md} §1 puts "role hierarchies beyond
 * {@code USER}/{@code ADMIN}" out of scope, so this enum is a boundary rather than a starting
 * point: a third role needs a task and an ADR, not an extra constant.
 *
 * <p>The persisted form is the constant name ({@code user.role} with a CHECK constraint). Spring
 * Security's {@code ROLE_} prefix is a framework detail and is applied where the authority is
 * built, not here — the domain does not know what a {@code GrantedAuthority} is.
 */
public enum Role {

    /** Plans and books their own trips. The only role a self-service sign-up can obtain. */
    USER,

    /** Manages accounts (PLAN §4.0.6). Seeded in dev/docker only. */
    ADMIN
}
