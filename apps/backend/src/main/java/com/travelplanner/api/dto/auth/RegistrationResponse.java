package com.travelplanner.api.dto.auth;

/**
 * The reply to {@code POST /auth/register} — the same body, always (ADR 009 §6).
 *
 * <p>{@code status} is a constant. That looks redundant until you consider what a variable field
 * would be: any value that differed between "created" and "already existed" would let anyone
 * enumerate registered addresses at one request each. The user learns which case they are in by
 * mail, on a channel only the address owner can read (task 09).
 */
public record RegistrationResponse(String status) {

    /** The only value this endpoint returns, whatever happened. */
    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";

    public static RegistrationResponse accepted() {
        return new RegistrationResponse(PENDING_VERIFICATION);
    }
}
