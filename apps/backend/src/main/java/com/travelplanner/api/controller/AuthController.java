package com.travelplanner.api.controller;

import com.travelplanner.api.dto.auth.AuthSessionResponse;
import com.travelplanner.api.dto.auth.CurrentUserResponse;
import com.travelplanner.api.dto.auth.LoginRequest;
import com.travelplanner.api.dto.auth.RegisterRequest;
import com.travelplanner.api.dto.auth.RegistrationResponse;
import com.travelplanner.api.security.ClientIpResolver;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.auth.AuthService;
import com.travelplanner.application.auth.IssuedSession;
import com.travelplanner.application.auth.UserProfileService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The session surface (PLAN §4.0.5 endpoint table, extended by ADR 009 §5 with
 * {@code /auth/refresh} and {@code /auth/logout-all}).
 *
 * <p>Routing and cookie plumbing only — every rule lives in {@code application/auth/}
 * (AGENTS.md: no business logic in controllers). What this class does own is the one thing that
 * genuinely belongs at the HTTP boundary: turning an {@link IssuedSession} into {@code Set-Cookie}
 * headers, so no token ever reaches a response body.
 *
 * <p><b>No endpoint here takes a user id from the caller.</b> Ownership always comes from
 * {@code @AuthenticationPrincipal}, which the security filter built from the signed cookie and a
 * live database read. A body-supplied id would be a caller asserting who they are.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiresDatabase
public class AuthController {

    private final AuthService auth;
    private final UserProfileService profiles;
    private final SessionCookieFactory cookies;

    public AuthController(AuthService auth, UserProfileService profiles,
            SessionCookieFactory cookies) {
        this.auth = auth;
        this.profiles = profiles;
        this.cookies = cookies;
    }

    /**
     * UC-A01. Always {@code 202} with the same body, whether the address was free or already
     * registered (ADR 009 §6) — {@code 201 Created} would itself be the answer to "does this
     * account exist?".
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request.toCommand());
        return ResponseEntity.accepted().body(RegistrationResponse.accepted());
    }

    /** UC-A04. Email or username plus password; sets both session cookies. */
    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        IssuedSession session = auth.login(request.toCommand(), ClientIpResolver.resolve(httpRequest));
        return sessionResponse(session);
    }

    /**
     * ADR 009 §3. Rotates the refresh cookie and issues a fresh access token. Public, because the
     * access token has by definition expired by the time a client needs this — the refresh cookie
     * is the credential.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(HttpServletRequest httpRequest) {
        String presented = cookies.readRefreshToken(httpRequest).orElse(null);
        return sessionResponse(auth.refresh(presented));
    }

    /**
     * UC-A10. Clears both cookies <em>and</em> invalidates the refresh token server-side — the
     * second half is what makes it a logout rather than a suggestion (ADR 009 §3).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        auth.logout(cookies.readRefreshToken(httpRequest).orElse(null));
        return clearedSession();
    }

    /**
     * ADR 009 §5 — sign out of every device. Bumps {@code token_version}, so access tokens already
     * in flight stop working on their next request rather than at their next expiry.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserContext caller) {
        auth.logoutAll(caller.userId());
        return clearedSession();
    }

    /** UC-A11 — the current profile and its linked providers, read live (ADR 009 §2). */
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal UserContext caller) {
        return CurrentUserResponse.from(profiles.load(caller.userId()));
    }

    private ResponseEntity<AuthSessionResponse> sessionResponse(IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.accessToken(session.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refreshToken(session.refreshToken()).toString())
                .body(AuthSessionResponse.of(profiles.load(session.userId())));
    }

    private ResponseEntity<Void> clearedSession() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookies.clearedAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshToken().toString())
                .build();
    }
}
