package com.travelplanner.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates a request across the frontend, the API, and the logs (PLAN §4.0.2-J2).
 *
 * <p>The frontend generates a UUID per call and sends it as {@code X-Request-Id}. This filter
 * puts it in the MDC — so every log line for the request carries it — and echoes it on the
 * response, which is what turns "the app broke" into a log query.
 *
 * <p>Runs first in the chain: an id assigned after authentication would be missing from exactly
 * the failures most worth tracing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** MDC key; referenced by the logging pattern in {@code application.yml}. */
    public static final String MDC_KEY = "requestId";

    /**
     * Client-supplied ids are echoed into responses and log files, so they are constrained rather
     * than trusted: no CR/LF (header splitting), no unbounded length, no control characters that
     * would corrupt a log line.
     */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled — leaving the id behind would mislabel the next request.
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String supplied) {
        return supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
    }
}
