package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One provider account linked to one {@link User} (PLAN §8).
 *
 * <p>Identity is {@code (provider, providerSubjectId)}, never the email address. Emails are
 * reassigned, forwarded, and reused; a provider subject is stable for the life of the account.
 * ADR 009 §4 is built on that distinction — treating email equality as proof of ownership is the
 * documented pre-hijack takeover pattern.
 *
 * @param email the address the provider reported at link time, kept for display and audit only.
 *        It is never the join key and may go stale.
 */
public record UserIdentity(
        UUID id,
        UUID userId,
        AuthProvider provider,
        String providerSubjectId,
        String email,
        Instant createdAt) {

    public UserIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(createdAt, "createdAt");
        providerSubjectId = requireSubject(providerSubjectId);
    }

    public Optional<String> emailIfPresent() {
        return Optional.ofNullable(email);
    }

    private static String requireSubject(String providerSubjectId) {
        String trimmed = providerSubjectId == null ? "" : providerSubjectId.trim();
        if (trimmed.isEmpty()) {
            throw ValidationFailedException.field("provider_subject_id", "must not be blank");
        }
        return trimmed;
    }
}
