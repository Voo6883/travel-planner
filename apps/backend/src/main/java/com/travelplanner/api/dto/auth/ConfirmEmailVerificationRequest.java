package com.travelplanner.api.dto.auth;

/**
 * {@code POST /api/v1/auth/verify-email/confirm} — UC-A08, ADR 009 §5.
 *
 * <p>The token travels in the body rather than in a query parameter. A query string is written to
 * access logs, kept in browser history, and sent in the {@code Referer} header of any request the
 * landing page makes — three places a single-use account credential should not be. The mailed
 * <em>link</em> does carry it in the URL, because a link has nowhere else to put it; the page then
 * moves it into a request body, which is as early as it can be moved.
 *
 * <p><strong>No Bean Validation annotations, on purpose.</strong> A {@code @NotBlank} or
 * {@code @Size} would answer a missing token with {@code validation_failed} and an unknown one with
 * {@code invalid_token}, which is two observable outcomes where the contract promises one. Every
 * malformed, missing, unknown, expired, and spent token ends at the same
 * {@link com.travelplanner.domain.exception.InvalidTokenException}, and the UI's correct reaction —
 * offer a fresh link — is the same for all of them.
 */
public record ConfirmEmailVerificationRequest(String token) {
}
