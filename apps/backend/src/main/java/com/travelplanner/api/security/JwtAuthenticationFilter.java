package com.travelplanner.api.security;

import com.travelplanner.application.auth.AccessTokenClaims;
import com.travelplanner.application.auth.JwtTokenService;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns the session cookie into a {@link UserContext} (ADR 002 transport, ADR 009 §1 and §2 rules).
 *
 * <p><b>The token is not the authority — the database row is.</b> A valid signature only proves the
 * token was issued by this system; it says nothing about whether the account still exists, is
 * still enabled, or has since revoked its sessions. So every authenticated request performs one
 * primary-key lookup and checks four things:
 *
 * <ol>
 *   <li>the account exists,
 *   <li>{@code enabled} is true,
 *   <li>{@code tv} still equals {@code user.token_version},
 *   <li>{@code iat} is not before {@code sessions_valid_after}.
 * </ol>
 *
 * <p>ADR 009 §2 accepts that cost explicitly — one indexed lookup at the §14 target of ~50
 * concurrent users — and gets a second thing for free: {@code email_verified} is read live rather
 * than frozen into a claim, so the UC-A08 gate opens the moment the user clicks the link instead
 * of up to thirty minutes later. No cache is added here; the ADR permits one only if profiling
 * shows a need, and rules out Redis for it in v1.
 *
 * <p><b>Nothing here rejects a request.</b> A missing, expired, tampered, or revoked token simply
 * leaves the context unauthenticated, and the entry point renders the one {@code unauthorized}
 * envelope. That is not laziness: an exception thrown inside a servlet filter never reaches
 * {@code GlobalExceptionHandler}, so a filter that threw would produce a container error page
 * instead of the contract's error shape.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService accessTokens;
    private final UserRepositoryPort users;
    private final SessionCookieFactory cookies;

    public JwtAuthenticationFilter(JwtTokenService accessTokens, UserRepositoryPort users,
            SessionCookieFactory cookies) {
        this.accessTokens = accessTokens;
        this.users = users;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookies.readAccessToken(request)
                    .flatMap(accessTokens::parse)
                    .flatMap(this::activeUserFor)
                    .ifPresent(JwtAuthenticationFilter::authenticate);
        }
        chain.doFilter(request, response);
    }

    /** @return the account, only when the token is still valid <em>for that account</em> */
    private Optional<User> activeUserFor(AccessTokenClaims claims) {
        return users.findById(claims.userId()).filter(user -> isStillValid(claims, user));
    }

    private static boolean isStillValid(AccessTokenClaims claims, User user) {
        if (!user.enabled() || claims.tokenVersion() != user.tokenVersion()) {
            return false;
        }
        Instant validAfter = user.sessionsValidAfter();
        // `iat` has second precision, so a token minted in the same second as a revocation is
        // rejected rather than accepted. Erring towards rejection is the only safe direction: the
        // cost is one re-login, and the alternative is a session that survived its own revocation.
        return validAfter == null || !claims.issuedAt().isBefore(validAfter);
    }

    private static void authenticate(User user) {
        UserContext principal = new UserContext(user.id(), user.email(),
                List.of(user.role().name()), user.emailVerified());
        // The ROLE_ prefix is Spring Security's convention for hasRole(); the domain enum stores
        // the bare name, and this is the boundary where the framework's spelling is applied.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
