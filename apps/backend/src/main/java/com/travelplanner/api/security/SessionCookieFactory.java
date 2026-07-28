package com.travelplanner.api.security;

import com.travelplanner.config.AuthSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the two session cookies, and the two that clear them (ADR 002, ADR 006, ADR 009 §3).
 *
 * <p>Every attribute is a decision:
 *
 * <ul>
 *   <li><b>{@code HttpOnly}</b> — the token is unreadable from JavaScript. This is the whole reason
 *       ADR 002 chose a cookie over an {@code Authorization} header: a token in {@code
 *       localStorage} is one XSS away from being exfiltrated.
 *   <li><b>{@code SameSite=Lax}</b> — locked by ADR 006. {@code None} was rejected because it
 *       requires {@code Secure}, which breaks plain-HTTP local development, and because it makes
 *       CSRF protection the only defence rather than a second one.
 *   <li><b>{@code Secure}</b> — on in production, off locally. A {@code Secure} cookie is silently
 *       dropped over {@code http://localhost}, so hard-coding it on would make the locked
 *       {@code docker compose up} experience fail with no visible cause.
 *   <li><b>{@code Path}</b> — the access token is sent to the whole API; the refresh token only to
 *       {@code /api/v1/auth}. There is no reason for a 14-day credential to travel on every list
 *       request, and ADR 006's pass-through proxy means the browser sees the same path the backend
 *       serves.
 *   <li><b>{@code Max-Age}</b> — mirrors the token's own lifetime, so a cookie never outlives what
 *       it carries. The server never trusts it: expiry is enforced by the signed {@code exp} claim
 *       and by {@code refresh_token.expires_at}, both of which a client cannot edit.
 * </ul>
 *
 * <p>A cleared cookie is written with {@code Max-Age=0} <em>and</em> the same name, path, secure,
 * and same-site attributes as the original. A browser matches on those; a clear that differs in
 * any of them leaves the original cookie in place.
 */
@Component
public class SessionCookieFactory {

    private final AuthSecurityProperties.Session properties;

    public SessionCookieFactory(AuthSecurityProperties properties) {
        this.properties = properties.getSession();
    }

    public ResponseCookie accessToken(String token) {
        return build(properties.getCookieName(), token, "/", properties.getAccessTokenTtl());
    }

    public ResponseCookie refreshToken(String token) {
        return build(properties.getRefreshCookieName(), token, properties.getRefreshCookiePath(),
                properties.getRefreshTokenTtl());
    }

    public ResponseCookie clearedAccessToken() {
        return build(properties.getCookieName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie clearedRefreshToken() {
        return build(properties.getRefreshCookieName(), "", properties.getRefreshCookiePath(),
                Duration.ZERO);
    }

    /** Reads the presented refresh token, if the caller sent one. */
    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, properties.getRefreshCookieName());
    }

    /** Reads the presented access token, if the caller sent one. */
    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, properties.getCookieName());
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
