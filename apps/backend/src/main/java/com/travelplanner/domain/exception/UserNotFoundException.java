package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No account with the requested id. Maps to {@code 404 user_not_found} (PLAN §4.0.6 "standard
 * errors: {@code forbidden}, {@code user_not_found}").
 *
 * <p><strong>Only the admin surface may raise this.</strong> Everywhere else, a resource the caller
 * does not own is {@code not_found} precisely so the API cannot be used to discover that somebody
 * else's resource exists. An administrator is already entitled to list every account, so naming the
 * condition tells them nothing they could not read off {@code GET /admin/users} — and it turns a
 * mistyped id from an unexplained 404 into a diagnosable one.
 */
public class UserNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "user_not_found";

    public UserNotFoundException() {
        super(CODE, "No account exists with that identifier.", Map.of());
    }
}
