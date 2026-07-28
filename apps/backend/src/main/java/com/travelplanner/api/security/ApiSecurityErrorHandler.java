package com.travelplanner.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.api.dto.error.ApiErrorResponse;
import com.travelplanner.api.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders security rejections in the one error envelope (PLAN §6.1).
 *
 * <p>Spring Security rejects requests inside the filter chain, before any controller and therefore
 * outside {@code GlobalExceptionHandler}'s reach. Without this class those rejections would return
 * the servlet container's default body — an HTML error page, or an empty 401 with a
 * {@code WWW-Authenticate} header — and the frontend, which resolves {@code code} to a translated
 * message, would have nothing to resolve.
 *
 * <p>Two outcomes, both already registered by task 06:
 *
 * <ul>
 *   <li>{@code 401 unauthorized} — no usable session. Identical for a missing cookie, an expired
 *       token, and a revoked one, because the caller's next step is the same in all three cases.
 *   <li>{@code 403 forbidden} — authenticated but not permitted. This is also where a missing or
 *       stale {@code X-XSRF-TOKEN} lands (ADR 006).
 * </ul>
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        write(response, ApiErrorCode.UNAUTHORIZED, "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException denial) throws IOException {
        write(response, ApiErrorCode.FORBIDDEN, "This action is not allowed.");
    }

    private void write(HttpServletResponse response, ApiErrorCode code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code.code(), message));
    }
}
