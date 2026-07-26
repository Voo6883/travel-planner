package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Optimistic-lock mismatch — ADR 008 §2. Maps to {@code 409 version_conflict}.
 *
 * <p>There is no implicit winner: the losing writer receives this and must re-read at
 * {@code details.current_version}, re-apply its edit, and retry. That is why the current version
 * is part of the contract and not merely a log line — without it the client cannot recover
 * without a blind refetch, and the agent-versus-user race in §4 of the ADR has no resolution.
 */
public class VersionConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "version_conflict";

    public VersionConflictException(int currentVersion) {
        super(CODE,
                "The resource was modified by someone else.",
                Map.of("current_version", currentVersion));
    }
}
