package com.travelplanner.api.controller;

import com.travelplanner.api.dto.auth.LinkProviderRequest;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.auth.ExternalIdentityService;
import com.travelplanner.application.auth.ProviderUnlinkService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /auth/providers/{provider}} — the explicit link confirmation ADR 009 §4 requires, and the
 * unlink it pairs with (UC-A09, UC-A11).
 *
 * <p>Both endpoints require a session, and that is the security property rather than a convention.
 * Auto-linking is refused whenever ownership of the existing account has not been demonstrated;
 * holding a session for it <em>is</em> the demonstration. A matching email address is not, which is
 * the whole of ADR 009 §4.
 *
 * <p><b>No endpoint here takes a user id.</b> The account always comes from
 * {@code @AuthenticationPrincipal}, which the security filter built from the signed cookie and a
 * live database read.
 */
@RestController
@RequestMapping("/api/v1/auth/providers")
@RequiresDatabase
public class ProviderLinkController {

    private final ExternalIdentityService external;
    private final ProviderUnlinkService unlinking;
    private final SessionCookieFactory cookies;

    public ProviderLinkController(ExternalIdentityService external, ProviderUnlinkService unlinking,
            SessionCookieFactory cookies) {
        this.external = external;
        this.unlinking = unlinking;
        this.cookies = cookies;
    }

    /**
     * UC-A09. Returns {@code 204} rather than the updated profile: {@code linked_providers} is
     * published by {@code GET /auth/me} and only there, so there is one place it can go stale.
     *
     * <p>{@code GITHUB} is rejected — its credential is a single-use authorization code that the
     * browser never holds. Linking GitHub is {@code GET /auth/oauth/github/start?mode=link}, which
     * is the same round trip with the same state protection.
     */
    @PostMapping("/{provider}")
    public ResponseEntity<Void> link(@PathVariable AuthProvider provider,
            @Valid @RequestBody LinkProviderRequest request,
            @AuthenticationPrincipal UserContext caller) {
        if (provider != AuthProvider.FIREBASE_GOOGLE) {
            throw ValidationFailedException.field("provider",
                    "only FIREBASE_GOOGLE accepts a credential here");
        }
        external.link(caller.userId(), provider, request.toCredential());
        return ResponseEntity.noContent().build();
    }

    /**
     * ADR 009 §4. Refused when it would strand the account, and every session is terminated when it
     * is not.
     *
     * <p>The cookies are cleared as well as revoked. Revocation is what makes the tokens useless;
     * clearing is what stops the browser sending a dead cookie on every subsequent request and
     * showing the user a signed-in shell that 401s.
     */
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@PathVariable AuthProvider provider,
            @AuthenticationPrincipal UserContext caller) {
        unlinking.unlink(caller.userId(), provider);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookies.clearedAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshToken().toString())
                .build();
    }
}
