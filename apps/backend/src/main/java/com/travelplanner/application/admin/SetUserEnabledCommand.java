package com.travelplanner.application.admin;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code PUT /admin/users/{userId}} — switch an account on or off (PLAN §4.0.6).
 *
 * <p>A command object rather than two loose arguments so the service signature stays
 * {@code (command, actor)}, which is the shape PLAN §4.0.6 writes out and the shape that keeps
 * every use-case method at two parameters however many fields the request grows.
 *
 * <p>Role is deliberately not here. An endpoint able to grant {@code ADMIN} is a
 * privilege-escalation surface, and {@code docs/AGENT-HARNESS.md} §1 puts role hierarchies out of
 * scope — the seeded account is the only administrator v1 creates.
 */
public record SetUserEnabledCommand(UUID userId, boolean enabled) {

    public SetUserEnabledCommand {
        Objects.requireNonNull(userId, "userId");
    }
}
