package com.travelplanner.application.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * The two tokens one successful authentication produces, plus who they belong to.
 *
 * <p>Raw values, deliberately: the caller is the controller, and its only job with them is to
 * write two {@code Set-Cookie} headers. Neither token ever appears in a response body — putting
 * the access token in JSON would hand it to any script on the page and undo the reason ADR 002
 * chose an httpOnly cookie over the {@code Authorization} header.
 */
public record IssuedSession(UUID userId, String accessToken, String refreshToken) {

    public IssuedSession {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }

    /** Tokens must not reach a log line, even accidentally. */
    @Override
    public String toString() {
        return "IssuedSession[userId=" + userId + ", accessToken=***, refreshToken=***]";
    }
}
