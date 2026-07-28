package com.travelplanner.config;

import com.travelplanner.api.security.ApiSecurityErrorHandler;
import com.travelplanner.api.security.CsrfCookieFilter;
import com.travelplanner.api.security.JwtAuthenticationFilter;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.auth.JwtTokenService;
import com.travelplanner.domain.port.UserRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * The HTTP authentication boundary (ADR 002, ADR 006, ADR 009).
 *
 * <h2>Stateless</h2>
 *
 * <p>No {@code HttpSession} is ever created. The session lives entirely in the signed cookie, which
 * is what lets the service scale horizontally without a shared session store — the property ADR 002
 * chose a JWT for and ADR 009 was careful to preserve while adding revocation.
 *
 * <h2>CSRF, and why it stays on</h2>
 *
 * <p>Cookie authentication means the browser attaches credentials to <em>any</em> request it makes,
 * including one triggered by another site. {@code SameSite=Lax} blocks most of that, but it is a
 * browser policy rather than a server check, so ADR 006 requires the double-submit token as well:
 * an {@code XSRF-TOKEN} cookie the page can read and an {@code X-XSRF-TOKEN} header it must echo.
 *
 * <p>{@code /auth/login} and {@code /auth/register} are protected too, even though the caller has
 * no session yet. Login CSRF — silently signing a victim into the attacker's account so their
 * activity accrues there — is a real attack, and exempting the endpoints would leave it open.
 *
 * <h2>What is public</h2>
 *
 * <p>Only the probes and the four endpoints a caller uses to <em>obtain</em> a session. Everything
 * else requires authentication by default, so an endpoint added by a later task is protected unless
 * somebody deliberately opens it — the safe direction for a mistake.
 *
 * <p>{@code logout} is public on purpose: it must succeed for a caller whose access token has
 * already expired, and a logout that can fail is one users learn to skip.
 */
@Configuration
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_GET = {"/api/v1/health", "/api/v1/ready"};

    /**
     * The endpoints a caller uses to obtain, refresh, or end a session — plus the four account
     * recovery paths from task 09, which are public for the same structural reason: every one of
     * them exists precisely because the caller cannot sign in.
     *
     * <p>The credential on the recovery paths is the single-use token in the mailed link, or nothing
     * at all for the two that only send mail. Those two are rate-limited per email and per address
     * instead (ADR 009 §6), because "no credential required" and "no limit" together is an open
     * mail relay.
     */
    private static final String[] PUBLIC_POST = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
        "/api/v1/auth/verify-email/confirm", "/api/v1/auth/verify-email/resend",
        "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
    };

    private final AuthSecurityProperties properties;
    private final ApiSecurityErrorHandler errors;
    private final SessionCookieFactory cookies;

    public SecurityConfig(AuthSecurityProperties properties, ApiSecurityErrorHandler errors,
            SessionCookieFactory cookies) {
        this.properties = properties;
        this.errors = errors;
        this.cookies = cookies;
    }

    /**
     * @param users absent in a database-less context — see {@link RequiresDatabase}. The chain is
     *        still built, and still denies everything that is not public, because a context with no
     *        user table has nobody to authenticate rather than nothing to protect.
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ObjectProvider<UserRepositoryPort> users, JwtTokenService accessTokens)
            throws Exception {

        CookieCsrfTokenRepository csrfTokens = csrfTokenRepository();

        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Every browser-facing form of authentication Spring Boot would otherwise switch
                // on. A default login form or Basic prompt on an API is a second, unreviewed way in.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .anonymous(Customizer.withDefaults())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(new CsrfCookieFilter(csrfTokens), AnonymousAuthenticationFilter.class);

        UserRepositoryPort userRepository = users.getIfAvailable();
        if (userRepository != null) {
            // Constructed here rather than declared as a bean: a Filter bean is also picked up by
            // Boot's servlet registration and would then run twice per request, once inside the
            // security chain and once outside it where no SecurityContext exists.
            http.addFilterBefore(new JwtAuthenticationFilter(accessTokens, userRepository, cookies),
                    AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * {@code withHttpOnlyFalse} is required, not a relaxation: the page has to read this cookie to
     * echo it back in a header, which is the entire double-submit mechanism. The value is a random
     * token that authorises nothing on its own — unlike the session cookie beside it, which stays
     * {@code HttpOnly}.
     */
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(properties.getSession().isSecure())
                .sameSite(properties.getSession().getSameSite()));
        return repository;
    }

    /**
     * Opts out of Spring Security 6's BREACH-mitigating token encoding. That scheme expects the
     * client to send the value rendered into the page body; a single-page app reads the raw cookie
     * instead, and the two do not match. This is the configuration Spring documents for SPAs.
     */
    private static CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
