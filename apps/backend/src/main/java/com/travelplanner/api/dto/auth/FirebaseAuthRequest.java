package com.travelplanner.api.dto.auth;

import com.travelplanner.domain.valueobject.ProviderCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/firebase} — the Firebase ID token, and nothing else (PLAN §4.0.5's
 * {@code FirebaseAuthRequest}).
 *
 * <p>No email, no uid, no {@code is_new_user} hint. Every one of those would be a fact the caller
 * asserts about themselves, and the server would then be trusting the client to describe the
 * identity it is about to authenticate. The token carries all of it, signed, and the server reads
 * it from there (ADR 004 Security).
 *
 * <p>{@code toString()} is not overridden and does not need to be: the field is never logged and
 * the record is never passed to a logger. {@link ProviderCredential}, which <em>is</em> handed
 * around, hides its secret.
 */
public record FirebaseAuthRequest(
        @NotBlank @Size(min = 20, max = 4096) String idToken) {

    /** {@code identifier} is absent: a bearer token identifies its own subject. */
    public ProviderCredential toCredential() {
        return new ProviderCredential(null, idToken);
    }
}
