package com.travelplanner.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes the {@code XSRF-TOKEN} cookie actually appear (ADR 006).
 *
 * <p>Spring Security loads its CSRF token <em>lazily</em>: on a safe request nothing resolves it,
 * so no cookie is written. A freshly loaded page that has only ever issued {@code GET}s would then
 * never receive a token, and its first mutating request would fail with 403 — permanently, since
 * nothing it can do produces the cookie. This filter is what ADR 006 means by "the SPA obtains the
 * initial token via a bootstrap {@code GET}": after this, any {@code GET} will do, including
 * {@code /health}.
 *
 * <p>It runs <em>after</em> {@code CsrfFilter}, which matters. Generating a token beforehand would
 * replace the one the client is in the middle of submitting, and every mutating request would fail
 * validation against a token that was rotated out from under it.
 *
 * <p>Two paths, because the token may or may not already have been resolved upstream:
 * touching the request attribute is the cheap case and lets the repository write the cookie
 * itself; the explicit generate-and-save is the fallback for when nothing upstream resolved it.
 * Doing only the first would leave the bootstrap dependent on framework-internal lazy-loading
 * behaviour — the exact thing this filter exists to stop depending on.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    private final CsrfTokenRepository repository;

    public CsrfCookieFilter(CsrfTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        CsrfToken deferred = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (deferred != null) {
            // The return value is deliberately unused: resolving a deferred token is what makes
            // the repository write the cookie.
            deferred.getToken();
        }
        if (needsACookie(request, response)) {
            // Reuses the token the chain already put on the request, so a client that is mid-flight
            // is never handed a different value than the one it is about to submit.
            CsrfToken token = deferred != null ? deferred : repository.generateToken(request);
            repository.saveToken(token, request, response);
        }
        chain.doFilter(request, response);
    }

    /**
     * Nothing to do when the caller already holds a token, or when resolving the deferred one
     * above already wrote the cookie. This filter runs before the chain reaches any controller, so
     * a {@code Set-Cookie} present at this point can only be that one.
     */
    private boolean needsACookie(HttpServletRequest request, HttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).isEmpty()
                && repository.loadToken(request) == null;
    }
}
