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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 *
 * <h2>Admin</h2>
 *
 * <p>{@code /api/v1/admin/**} requires the {@code ROLE_ADMIN} authority here <em>and</em> carries
 * {@code @PreAuthorize("hasRole('ADMIN')")} on the controller (PLAN §4.0.6). The two are
 * independent on purpose: the chain rule covers the whole prefix, including a controller a later
 * task adds and forgets to annotate, while the annotation travels with the code it guards and
 * survives a change to this file. One misconfiguration has to defeat both to open the surface.
 *
 * <p>{@code ROLE_ADMIN} is the Spring authority; the stored and published role value is
 * {@code ADMIN}, and {@code JwtAuthenticationFilter} is the single place the prefix is applied.
 * {@code hasRole} re-adds it, so its argument is the bare name — the two spellings are not
 * interchangeable and mixing them denies everybody.
 */
@Configuration
@EnableConfigurationProperties(AuthSecurityProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The probes, plus GitHub's redirect round trip.
     *
     * <p>Both OAuth endpoints are public because the browser reaching them has, by definition, no
     * session yet — obtaining one is what the round trip is for. The callback's credential is the
     * single-use {@code state} it must echo from an {@code httpOnly} cookie (ADR 004 Security), and
     * {@code ?mode=link} is refused by the controller without a live session, so "public" here does
     * not mean "unauthenticated callers can do anything".
     *
     * <p>They are {@code GET} because a provider redirect is a top-level navigation and there is no
     * other verb a browser can arrive with. That also puts them outside CSRF protection, which is
     * why {@code state} exists.
     */
    private static final String[] PUBLIC_GET = {
        "/api/v1/health", "/api/v1/ready",
        "/api/v1/auth/oauth/github/start", "/api/v1/auth/oauth/github/callback",
    };

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
        // Gmail sign-up and sign-in are one endpoint (PLAN §4.0.5), so it has to be reachable
        // without a session. `/auth/providers/**` deliberately is not: linking is the explicit,
        // authenticated confirmation ADR 009 §4 requires.
        "/api/v1/auth/firebase",
    };

    /** Every account-administration endpoint (PLAN §4.0.6), present and future. */
    private static final String ADMIN_PREFIX = "/api/v1/admin/**";

    /**
     * The <em>role</em> name, not the authority. {@code hasRole} prepends {@code ROLE_} itself, so
     * this is {@code ADMIN} and the authority it matches is {@code ROLE_ADMIN} —
     * {@code hasRole("ROLE_ADMIN")} would look for {@code ROLE_ROLE_ADMIN} and deny everybody.
     */
    private static final String ADMIN_ROLE = "ADMIN";

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
                        // PLAN §4.0.6. Written against the prefix rather than the four known
                        // paths, so an admin endpoint added later is protected before anyone
                        // remembers to annotate it.
                        .requestMatchers(ADMIN_PREFIX).hasRole(ADMIN_ROLE)
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
