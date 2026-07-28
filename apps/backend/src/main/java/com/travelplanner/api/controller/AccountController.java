package com.travelplanner.api.controller;

import com.travelplanner.api.dto.auth.AcceptedResponse;
import com.travelplanner.api.dto.auth.ConfirmEmailVerificationRequest;
import com.travelplanner.api.dto.auth.EmailOnlyRequest;
import com.travelplanner.api.security.ClientIpResolver;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.account.AccountDeletionService;
import com.travelplanner.application.account.EmailVerificationService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email verification and account closure (UC-A08, UC-A13, UC-A14).
 *
 * <p>Separate from {@code AuthController}, which owns the session surface — obtaining, refreshing,
 * and ending a session. These endpoints change the <em>account</em>, and separating them is what
 * keeps either controller's dependencies at three.
 *
 * <p>Routing and cookie plumbing only; every rule lives in {@code application/account/}
 * ({@code AGENTS.md}: no business logic in controllers). <b>No endpoint here takes a user id from
 * the caller</b> — ownership always comes from {@code @AuthenticationPrincipal}, built by the
 * security filter from the signed cookie and a live database read.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiresDatabase
public class AccountController {

    private final EmailVerificationService verification;
    private final AccountDeletionService deletion;
    private final SessionCookieFactory cookies;

    public AccountController(EmailVerificationService verification, AccountDeletionService deletion,
            SessionCookieFactory cookies) {
        this.verification = verification;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    /**
     * UC-A08 — the mailed link was followed. Public, because the whole point of the gate is that the
     * account cannot sign in yet; the single-use token is the credential.
     */
    @PostMapping("/verify-email/confirm")
    public ResponseEntity<Void> confirmVerification(
            @RequestBody ConfirmEmailVerificationRequest request) {
        verification.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * UC-A13 — send the verification mail again. Always {@code 202} with the same body, whether the
     * address is registered, unregistered, or already verified (ADR 009 §6).
     */
    @PostMapping("/verify-email/resend")
    public ResponseEntity<AcceptedResponse> resendVerification(
            @Valid @RequestBody EmailOnlyRequest request, HttpServletRequest httpRequest) {
        verification.resend(request.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted().body(AcceptedResponse.accepted());
    }

    /**
     * UC-A14 — close your own account: soft delete, PII anonymised, every session terminated
     * (ADR 009 §1).
     *
     * <p>The cookies are cleared as well as revoked. Revocation is what makes the tokens useless;
     * clearing is what stops the browser from sending a dead cookie on every subsequent request and
     * showing the user a signed-in shell that 401s.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(@AuthenticationPrincipal UserContext caller) {
        deletion.delete(caller.userId());
        return clearedSession();
    }

    private ResponseEntity<Void> clearedSession() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookies.clearedAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshToken().toString())
                .build();
    }
}
