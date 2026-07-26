package com.travelplanner.domain.model;

import com.travelplanner.domain.exception.VersionConflictException;

/**
 * An aggregate the agent and the user can both write, and which therefore carries an optimistic
 * lock (ADR 008 §1).
 *
 * <p>Two mechanisms guard the same aggregate and they are not redundant:
 *
 * <ol>
 *   <li>{@link #requireVersion(Versioned, int)} compares the version the <em>client</em> based its
 *       edit on against current state, before any write. This is the case ADR 008 §2 describes —
 *       a stale form or a stale agent tool call — and it is the only one that can report
 *       {@code details.current_version}, because at that moment the current row is in hand.</li>
 *   <li>JPA {@code @Version} on the entity catches the narrower race where two transactions read
 *       the same version and commit simultaneously. That one surfaces from the persistence
 *       provider and is translated in the repository adapter.</li>
 * </ol>
 *
 * <p>Checking only (2) would leave {@code current_version} unavailable without a second query;
 * checking only (1) would leave a genuine concurrent commit undetected.
 */
public interface Versioned {

    /** Monotonic revision. 0 for an aggregate that has never been persisted. */
    int version();

    /**
     * Guards a write against the version the caller believed it was editing.
     *
     * @throws VersionConflictException carrying the true current version, which the loser needs in
     *         order to re-read and re-apply rather than blindly refetch
     */
    static void requireVersion(Versioned current, int expectedVersion) {
        if (current.version() != expectedVersion) {
            throw new VersionConflictException(current.version());
        }
    }
}
