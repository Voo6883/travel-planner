package com.travelplanner.application.admin;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code PUT /admin/users/{userId}/reset-password} — an administrator sets a temporary password
 * (UC-A16, PLAN §4.0.6).
 *
 * <p>The administrator communicates the value out of band; nothing returns it, nothing logs it, and
 * no mail carries it. That out-of-band channel is the only thing standing between "an administrator
 * account was taken over" and "every account was taken over", which is why the value never appears
 * in a response body a stolen session could read.
 *
 * <p>{@code toString} is overridden for the same reason {@code MailMessage} redacts a recipient: a
 * record's generated {@code toString} prints every component, and a command object is exactly the
 * kind of thing that ends up inside an exception message or a debug log by accident.
 */
public record AdminResetPasswordCommand(UUID userId, String newPassword) {

    public AdminResetPasswordCommand {
        Objects.requireNonNull(userId, "userId");
    }

    @Override
    public String toString() {
        return "AdminResetPasswordCommand[userId=" + userId + ", newPassword=***]";
    }
}
