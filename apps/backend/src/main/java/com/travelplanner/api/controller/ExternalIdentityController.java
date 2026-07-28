package com.travelplanner.api.controller;

import com.travelplanner.api.dto.auth.AuthSessionResponse;
import com.travelplanner.api.dto.auth.FirebaseAuthRequest;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.auth.ExternalIdentityService;
import com.travelplanner.application.auth.ExternalSignIn;
import com.travelplanner.application.auth.UserProfileService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/firebase} — Gmail sign-up and sign-in on one endpoint (PLAN §4.0.5, UC-A02,
 * UC-A05).
 *
 * <p>Routing and cookie plumbing only; every rule lives in {@code application/auth/}
 * ({@code AGENTS.md}: no business logic in controllers). What it does own is the one thing that
 * belongs at the HTTP boundary — turning an {@code IssuedSession} into {@code Set-Cookie} headers,
 * so no token reaches a response body.
 *
 * <p>Separate from {@link AuthController} rather than a fourth method on it: that class already
 * holds the local session surface at three dependencies, and this one needs the external identity
 * service instead of {@code AuthService}.
 *
 * <p>The response is the same {@code AuthSessionResponse} local sign-in returns, with
 * {@code is_new_user} and {@code provider_linked} filled in. One session model for all three
 * providers is what PLAN §4.0.5 locks, and it is why nothing Firebase-shaped appears in the body.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiresDatabase
public class ExternalIdentityController {

    private final ExternalIdentityService external;
    private final UserProfileService profiles;
    private final SessionCookieFactory cookies;

    public ExternalIdentityController(ExternalIdentityService external, UserProfileService profiles,
            SessionCookieFactory cookies) {
        this.external = external;
        this.profiles = profiles;
        this.cookies = cookies;
    }

    /**
     * Public, like {@code /auth/login}: the caller has no session yet — obtaining one is the point.
     * CSRF still applies, because signing a victim into the attacker's account is a real attack and
     * exempting the endpoint would leave it open (ADR 006).
     */
    @PostMapping("/firebase")
    public ResponseEntity<AuthSessionResponse> authenticateWithFirebase(
            @Valid @RequestBody FirebaseAuthRequest request) {
        ExternalSignIn signIn =
                external.signIn(AuthProvider.FIREBASE_GOOGLE, request.toCredential());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.accessToken(signIn.session().accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.refreshToken(signIn.session().refreshToken()).toString())
                .body(AuthSessionResponse.of(profiles.load(signIn.session().userId()), signIn));
    }
}
