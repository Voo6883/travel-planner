package com.travelplanner.api.dto;

import com.travelplanner.domain.exception.ValidationFailedException;

/**
 * Marker for request bodies that mutate a versioned aggregate — ADR 008 §2.
 *
 * <p>The version travels in the body, not in {@code ETag}/{@code If-Match}, because a body field
 * is expressible in OpenAPI and flows through codegen into the typed client. Header-based
 * concurrency would need hand-plumbed handling inside the generated-client layer this project
 * forbids editing.
 *
 * <p>Implementations are records whose component carries the constraint, which is what produces
 * the {@code 400 validation_failed} that ADR 008 requires for a missing value:
 *
 * <pre>{@code
 * public record UpdateTripBriefRequest(
 *         @NotNull Integer expectedVersion,
 *         List<String> destinations) implements VersionedMutation {}
 * }</pre>
 *
 * <p>{@link #require(Integer)} is the same guarantee for callers Bean Validation never sees —
 * an LLM tool invocation, a scheduled job — so "omitted version" can never degrade into a silent
 * force-overwrite on any path into the aggregate.
 */
public interface VersionedMutation {

    /** The version the client based its edit on. Serialised as {@code expected_version}. */
    Integer expectedVersion();

    /**
     * @throws ValidationFailedException when the version is absent — never a force-overwrite
     */
    static int require(Integer expectedVersion) {
        if (expectedVersion == null) {
            throw ValidationFailedException.field("expected_version", "must not be null");
        }
        return expectedVersion;
    }
}
