package com.travelplanner.api.security;

import com.travelplanner.application.auth.OAuthState;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.config.IdentityProviderProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Carries the OAuth {@code state} across one redirect round trip (ADR 004 Security).
 *
 * <p>Every attribute mirrors {@link SessionCookieFactory}'s reasoning, with two differences that
 * follow from what this cookie is:
 *
 * <ul>
 *   <li><b>{@code Path=/api/v1/auth/oauth}</b> — narrower than the refresh cookie's. Only the
 *       callback ever reads it, so it has no reason to be attached to anything else.
 *   <li><b>Minutes, not days</b> — it is a nonce for one redirect. A long-lived {@code state}
 *       cookie is a replayable one.
 * </ul>
 *
 * <p>{@code HttpOnly}, because the whole mechanism rests on the browser holding a value that no
 * script can read or forge: a callback arriving from another site brings no cookie and therefore no
 * match. {@code SameSite=Lax} still sends it, because a provider redirect is a top-level
 * navigation — the one cross-site case {@code Lax} deliberately permits, and the reason
 * {@code Strict} would break the flow entirely.
 */
@Component
public class OAuthStateCookieFactory {

    /** Named like the session cookies so the whole family is recognisable in a browser inspector. */
    static final String COOKIE_NAME = "tp_oauth_state";

    static final String COOKIE_PATH = "/api/v1/auth/oauth";

    private final AuthSecurityProperties.Session session;
    private final Duration ttl;

    public OAuthStateCookieFactory(AuthSecurityProperties security,
            IdentityProviderProperties identity) {
        this.session = security.getSession();
        this.ttl = identity.getGithub().getStateTtl();
    }

    public ResponseCookie issue(OAuthState state) {
        return build(state.cookieValue(), ttl);
    }

    /**
     * Written on every callback, success or failure. A state that survives its round trip is a
     * state that can be replayed, so it is single-use by being cleared whether or not it matched.
     */
    public ResponseCookie cleared() {
        return build("", Duration.ZERO);
    }

    public Optional<OAuthState> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .flatMap(value -> OAuthState.parse(value).stream())
                .findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(session.isSecure())
                .sameSite(session.getSameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
