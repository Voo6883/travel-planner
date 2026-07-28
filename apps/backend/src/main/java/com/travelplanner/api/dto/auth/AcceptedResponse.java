package com.travelplanner.api.dto.auth;

/**
 * The reply to {@code POST /auth/password/forgot} and {@code POST /auth/verify-email/resend} — the
 * same body, always (ADR 009 §6).
 *
 * <p>{@code status} is a constant, for the same reason {@link RegistrationResponse}'s is: any field
 * that varied with whether the address is registered would let anyone enumerate accounts at one
 * request each. The submitted address learns the real outcome by mail, on a channel only its owner
 * can read.
 *
 * <p>A separate type from {@link RegistrationResponse} rather than a shared one, because the two
 * publish different vocabularies — {@code PENDING_VERIFICATION} promises a verification mail is
 * coming, which is not what a forgot-password request produces.
 */
public record AcceptedResponse(String status) {

    /** The only value these endpoints return, whatever happened. */
    public static final String ACCEPTED = "ACCEPTED";

    public static AcceptedResponse accepted() {
        return new AcceptedResponse(ACCEPTED);
    }
}
