package com.travelplanner.api.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/admin/users/{userId}} — PLAN §4.0.6.
 *
 * <p>{@code Boolean} rather than {@code boolean}, and {@code @NotNull}. A primitive would bind a
 * missing {@code enabled} to {@code false}, so a malformed request would silently disable an
 * account and terminate every session it holds. That is the same "absent must never mean an
 * irreversible default" rule ADR 008 §2 applies to {@code expected_version}.
 *
 * <p>No {@code role} field. An endpoint able to grant {@code ADMIN} is a privilege-escalation
 * surface, and {@code docs/AGENT-HARNESS.md} §1 puts role hierarchies out of scope.
 */
public record UpdateAdminUserRequest(@NotNull Boolean enabled) {
}
