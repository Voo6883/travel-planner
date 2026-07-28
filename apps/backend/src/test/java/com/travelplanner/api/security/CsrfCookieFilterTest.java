package com.travelplanner.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * The CSRF bootstrap from ADR 006: a page that has only ever issued {@code GET}s must still end up
 * holding a token, or its first mutating request fails with 403 and nothing it can do will fix
 * that.
 *
 * <p>Tested here rather than through MockMvc because {@code spring-security-test}'s {@code csrf()}
 * post-processor replaces the application's {@code CsrfTokenRepository} with an in-request double
 * on the shared filter chain — so in a suite where any test signs in, no test can observe a real
 * cookie being written.
 */
class CsrfCookieFilterTest {

    private final CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    private final CsrfCookieFilter filter = new CsrfCookieFilter(repository);

    @Test
    void writesTheCookieOnASafeRequestThatCarriesNoTokenYet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Cookie issued = response.getCookie("XSRF-TOKEN");
        assertThat(issued).isNotNull();
        assertThat(issued.getValue()).isNotBlank();
        // Readable by design: the page has to echo it in X-XSRF-TOKEN, which is the entire
        // double-submit mechanism. It authorises nothing on its own.
        assertThat(issued.isHttpOnly()).isFalse();
    }

    @Test
    void rendersTheTokenUpstreamAlreadyResolvedRatherThanMintingASecondOne() throws Exception {
        // When CsrfFilter has already deferred a token onto the request, touching that value is
        // what makes the repository write it. Generating our own instead would rotate the token
        // out from under a client that is mid-request.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken upstream = repository.generateToken(request);
        request.setAttribute(CsrfToken.class.getName(), upstream);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getCookie("XSRF-TOKEN").getValue()).isEqualTo(upstream.getToken());
    }

    @Test
    void leavesAnExistingTokenAloneSoAClientIsNotRotatedMidFlight() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setCookies(new Cookie("XSRF-TOKEN", "token-the-client-already-holds"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}
