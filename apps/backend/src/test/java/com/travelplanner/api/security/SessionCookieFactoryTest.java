package com.travelplanner.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.config.AuthSecurityProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Session cookie attributes (task 08 Definition of Done: "session cookie attributes are covered by
 * tests").
 *
 * <p>Every assertion here corresponds to an attack these attributes close, and each one is the
 * kind of setting that is silently wrong until someone looks: an {@code HttpOnly} that got dropped
 * still works perfectly in every functional test, right up until an XSS exfiltrates the session.
 */
class SessionCookieFactoryTest {

    private final AuthSecurityProperties properties = new AuthSecurityProperties();
    private final SessionCookieFactory cookies = new SessionCookieFactory(properties);

    @Test
    void theAccessCookieIsHttpOnlyLaxAndScopedToTheWholeApi() {
        ResponseCookie cookie = cookies.accessToken("signed.jwt.value");

        assertThat(cookie.getName()).isEqualTo("tp_session");
        assertThat(cookie.getValue()).isEqualTo("signed.jwt.value");
        // HttpOnly is the whole reason ADR 002 chose a cookie over an Authorization header.
        assertThat(cookie.isHttpOnly()).isTrue();
        // Lax is locked by ADR 006; None would require Secure and break plain-HTTP local dev.
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void theRefreshCookieLastsFourteenDaysAndIsScopedToTheAuthRoutes() {
        ResponseCookie cookie = cookies.refreshToken("opaque-refresh-token");

        assertThat(cookie.getName()).isEqualTo("tp_refresh");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        // A fourteen-day credential has no business travelling on every list request.
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void secureIsOffByDefaultBecauseTheLockedLocalRuntimeIsPlainHttp() {
        // A Secure cookie is silently dropped over http://localhost, so defaulting it on would
        // break `docker compose up` with no visible cause (ADR 006).
        assertThat(cookies.accessToken("x").isSecure()).isFalse();
        assertThat(cookies.refreshToken("x").isSecure()).isFalse();
    }

    @Test
    void secureIsOnWhereTheProfileTurnsItOnAsProductionDoes() {
        properties.getSession().setSecure(true);

        assertThat(cookies.accessToken("x").isSecure()).isTrue();
        assertThat(cookies.refreshToken("x").isSecure()).isTrue();
    }

    @Test
    void aClearedCookieRepeatsEveryAttributeABrowserMatchesOn() {
        properties.getSession().setSecure(true);

        ResponseCookie clearedAccess = cookies.clearedAccessToken();
        ResponseCookie clearedRefresh = cookies.clearedRefreshToken();

        // Max-Age=0 alone is not enough: a browser matches name, path, secure, and same-site, and
        // a clear that differs in any of them leaves the original cookie in place.
        assertThat(clearedAccess.getMaxAge()).isZero();
        assertThat(clearedAccess.getValue()).isEmpty();
        assertThat(clearedAccess.getPath()).isEqualTo(cookies.accessToken("x").getPath());
        assertThat(clearedAccess.isSecure()).isTrue();
        assertThat(clearedAccess.getSameSite()).isEqualTo("Lax");

        assertThat(clearedRefresh.getMaxAge()).isZero();
        assertThat(clearedRefresh.getPath()).isEqualTo(cookies.refreshToken("x").getPath());
    }

    @Test
    void readsTheTokensBackFromARequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("tp_session", "access"), new Cookie("tp_refresh", "refresh"));

        assertThat(cookies.readAccessToken(request)).contains("access");
        assertThat(cookies.readRefreshToken(request)).contains("refresh");
    }

    @Test
    void treatsAnAbsentOrEmptyCookieAsNoCookie() {
        MockHttpServletRequest none = new MockHttpServletRequest();
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("tp_session", ""));

        assertThat(cookies.readAccessToken(none)).isEmpty();
        assertThat(cookies.readAccessToken(blank)).isEqualTo(Optional.empty());
    }
}
