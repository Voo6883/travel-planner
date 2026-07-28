package com.travelplanner.api.controller;

import com.travelplanner.api.dto.auth.AcceptedResponse;
import com.travelplanner.api.dto.auth.ChangePasswordRequest;
import com.travelplanner.api.dto.auth.EmailOnlyRequest;
import com.travelplanner.api.dto.auth.ResetPasswordRequest;
import com.travelplanner.api.security.ClientIpResolver;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.account.PasswordChangeService;
import com.travelplanner.application.account.PasswordResetService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The password surface: forgot, reset, and self-service change (UC-A07, UC-A12).
 *
 * <p>Routing only — every rule lives in {@code application/account/}. What this class does own is
 * the one thing that belongs at the HTTP boundary: clearing the caller's session cookies after a
 * change that revoked them, so the browser stops sending a cookie the server will reject.
 */
@RestController
@RequestMapping("/api/v1/auth/password")
@RequiresDatabase
public class PasswordController {

    private final PasswordResetService passwordReset;
    private final PasswordChangeService passwordChange;
    private final SessionCookieFactory cookies;

    public PasswordController(PasswordResetService passwordReset, PasswordChangeService passwordChange,
            SessionCookieFactory cookies) {
        this.passwordReset = passwordReset;
        this.passwordChange = passwordChange;
        this.cookies = cookies;
    }

    /**
     * UC-A07 step one. Always {@code 202} with the same body — for a registered address, an
     * unregistered one, and an account that has no local password alike (ADR 009 §6).
     */
    @PostMapping("/forgot")
    public ResponseEntity<AcceptedResponse> forgot(@Valid @RequestBody EmailOnlyRequest request,
            HttpServletRequest httpRequest) {
        passwordReset.forgot(request.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted().body(AcceptedResponse.accepted());
    }

    /**
     * UC-A07 step two. Public: the single-use token is the credential, and the caller by definition
     * cannot sign in.
     *
     * <p>No cookies are cleared here, because the caller has none — they are signed out, which is
     * why they are resetting. The sessions that <em>are</em> revoked belong to whoever was signed in
     * before, which is the point of the endpoint.
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * UC-A12. Requires the current password, and ends every session including this one — so the
     * response clears the caller's own cookies and the client must sign in again.
     *
     * <p>{@code PUT} because it replaces one field of the caller's own account; {@code PATCH} is
     * forbidden project-wide (ADR 008 §3).
     */
    @PutMapping
    public ResponseEntity<Void> change(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserContext caller) {
        passwordChange.change(caller.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookies.clearedAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshToken().toString())
                .build();
    }
}
