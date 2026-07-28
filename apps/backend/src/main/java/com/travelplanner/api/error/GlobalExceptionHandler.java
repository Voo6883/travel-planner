package com.travelplanner.api.error;

import com.travelplanner.api.dto.error.ApiErrorResponse;
import com.travelplanner.domain.exception.DomainException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every failure into the one PLAN §6.1 envelope, so a client needs exactly one error
 * parser and one i18n lookup regardless of which layer failed.
 *
 * <p>Nothing here decides <em>whether</em> something is an error — that is the domain's job. This
 * class only decides how an error is rendered on the wire.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Registered domain failures. The status comes from the catalog, never from the exception, so
     * an unregistered code cannot invent its own HTTP semantics on the way out.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException exception) {
        ApiErrorCode code = ApiErrorCode.fromCode(exception.code()).orElse(null);
        if (code == null) {
            log.error("Unregistered error code '{}' — register it in api/openapi/errors.yaml",
                    exception.code(), exception);
            return internalError();
        }
        return ResponseEntity.status(code.status())
                .body(new ApiErrorResponse(code.code(), exception.getMessage(), exception.details()));
    }

    /** Request body failed Bean Validation — the {@code expected_version} case from ADR 008. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(wireName(error.getField()), messageOf(error.getDefaultMessage()));
        }
        return validationFailed(fields);
    }

    /** Value-object or method-level constraints (`@Validated` services, path and query params). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(wireName(lastNode(violation.getPropertyPath().toString())),
                        messageOf(violation.getMessage())));
        return validationFailed(fields);
    }

    /**
     * Malformed or unbindable input: unparseable JSON, a missing required query parameter, a
     * {@code page=abc}. These are client mistakes, so 400 — not the 500 the catch-all would give.
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class,
    })
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(Exception exception) {
        // The raw message can echo internal type names and the request body, so it is logged
        // rather than returned.
        log.debug("Rejected malformed request", exception);
        return validationFailed(Map.of());
    }

    /**
     * Method security denied the call — {@code @PreAuthorize("hasRole('ADMIN')")} on the admin
     * controller (PLAN §4.0.6).
     *
     * <p>{@code ApiSecurityErrorHandler} renders the identical envelope for a denial raised inside
     * the filter chain, but it never sees this one: {@code @PreAuthorize} runs in a proxy around the
     * controller method, so its {@code AuthorizationDeniedException} is resolved here first. Without
     * this handler the catch-all below would turn a correct authorisation decision into
     * {@code 500 internal_error} — a security control that works but reports itself as a bug.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException denial) {
        log.debug("Method security denied a call", denial);
        return ResponseEntity.status(ApiErrorCode.FORBIDDEN.status())
                .body(ApiErrorResponse.of(ApiErrorCode.FORBIDDEN.code(),
                        "This action is not allowed."));
    }

    /**
     * An unmatched path is a client error, not a server fault. Without this, the catch-all below
     * swallows Spring's no-handler exception and every typo returns 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(ApiErrorCode.NOT_FOUND.status())
                .body(ApiErrorResponse.of(ApiErrorCode.NOT_FOUND.code(),
                        "The requested resource does not exist."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // Detail stays server-side: the client gets a code it can map, the operator gets the trace.
        log.error("Unhandled failure while serving request", exception);
        return internalError();
    }

    private static ResponseEntity<ApiErrorResponse> validationFailed(Map<String, String> fields) {
        Map<String, Object> details = fields.isEmpty() ? Map.of() : Map.of("fields", fields);
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status())
                .body(new ApiErrorResponse(ApiErrorCode.VALIDATION_FAILED.code(),
                        "The request is not valid.", details));
    }

    private static ResponseEntity<ApiErrorResponse> internalError() {
        return ResponseEntity.status(ApiErrorCode.INTERNAL_ERROR.status())
                .body(ApiErrorResponse.of(ApiErrorCode.INTERNAL_ERROR.code(),
                        "An unexpected error occurred."));
    }

    /**
     * Bean Validation reports the Java property name; the contract publishes the snake_case wire
     * name. Reporting {@code expectedVersion} for a field the client sent as
     * {@code expected_version} makes the error unusable for form-field highlighting.
     */
    private static String wireName(String property) {
        StringBuilder wire = new StringBuilder(property.length() + 4);
        for (int i = 0; i < property.length(); i++) {
            char character = property.charAt(i);
            if (Character.isUpperCase(character)) {
                wire.append('_').append(Character.toLowerCase(character));
            } else {
                wire.append(character);
            }
        }
        return wire.toString();
    }

    private static String lastNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    private static String messageOf(String message) {
        return message == null || message.isBlank() ? "is not valid" : message;
    }
}
