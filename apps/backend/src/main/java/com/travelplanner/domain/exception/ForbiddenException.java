package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Authenticated, but not permitted to perform this action. Maps to {@code 403 forbidden}, the code
 * task 06 registered.
 *
 * <p>Spring Security raises its own {@code AccessDeniedException} for a caller who lacks the
 * required authority, and {@code ApiSecurityErrorHandler} renders that as the same envelope. This
 * exception is for the refusals a <em>service</em> makes, where the caller holds the right role and
 * the action is still not allowed — an administrator disabling their own account being the case
 * PLAN §4.0.6 creates.
 *
 * <p>The message is deliberately generic and carries no {@code details}. A refusal that explains
 * which internal rule fired is a refusal that can be probed.
 */
public class ForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "forbidden";

    public ForbiddenException(String message) {
        super(CODE, message, Map.of());
    }

    public ForbiddenException() {
        this("This action is not allowed.");
    }
}
