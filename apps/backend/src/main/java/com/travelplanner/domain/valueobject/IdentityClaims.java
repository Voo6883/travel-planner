package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.Objects;

/**
 * What an identity provider asserts about a caller once it has verified them (PLAN §4.0.5).
 *
 * <p>The join key is {@code (provider, subject)} — never {@code email}. ADR 009 §4 calls treating
 * email equality as proof of ownership the documented pre-hijack takeover pattern, so the email
 * here is descriptive only: it is what the provider reported, and task 10 decides separately
 * whether that is enough to link anything.
 *
 * @param subject the provider's immutable identifier for this account. For {@code LOCAL} it is the
 *        {@code user.id} this system already owns.
 * @param emailVerified whether the <em>provider</em> considers the address confirmed. Google and
 *        GitHub can assert this; the local adapter reports the stored {@code email_verified}.
 */
public record IdentityClaims(
        AuthProvider provider, String subject, String email, boolean emailVerified) {

    public IdentityClaims {
        Objects.requireNonNull(provider, "provider");
        subject = requireSubject(subject);
    }

    private static String requireSubject(String subject) {
        String trimmed = subject == null ? "" : subject.trim();
        if (trimmed.isEmpty()) {
            throw ValidationFailedException.field("provider_subject_id", "must not be blank");
        }
        return trimmed;
    }
}
