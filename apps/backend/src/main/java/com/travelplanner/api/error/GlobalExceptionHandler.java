package com.travelplanner.api.error;

import com.travelplanner.api.dto.error.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Last-resort handler so an unexpected failure still returns the PLAN §6.1 envelope instead of a
 * Spring default body containing a stack trace or internal path.
 *
 * <p>Scope note: this is the catch-all only. The registered error catalog, typed domain
 * exceptions, and validation mapping are owned by tasks/06-openapi-error-platform.md.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * An unmatched path is a client error, not a server fault. Without this, the catch-all below
     * swallows Spring's no-handler exception and every typo returns 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("not_found", "The requested resource does not exist."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // Detail stays server-side: the client gets a code it can map, the operator gets the trace.
        log.error("Unhandled exception while serving request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("internal_error", "An unexpected error occurred."));
    }
}
