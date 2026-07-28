package com.travelplanner.api.controller;

import com.travelplanner.api.security.OAuthStateCookieFactory;
import com.travelplanner.api.security.SessionCookieFactory;
import com.travelplanner.application.auth.ExternalSignIn;
import com.travelplanner.application.auth.OAuthFlowService;
import com.travelplanner.application.auth.OAuthState;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.DomainException;
import com.travelplanner.domain.exception.InvalidOAuthStateException;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The GitHub OAuth round trip (PLAN §4.0.5, UC-A03, UC-A06).
 *
 * <h2>Redirects, not JSON</h2>
 *
 * <p>The caller of both endpoints is a browser following a redirect, not a script reading a body.
 * Every outcome is therefore a {@code 302} to a frontend URL, and a failure carries the registered
 * error code as {@code ?error=…} so the UI renders the same translated message it would for the
 * error envelope. Returning JSON here would leave the user staring at a raw payload on the API
 * origin with no route back into the product.
 *
 * <p><b>Every redirect target comes from configuration.</b> Nothing the caller sends is ever echoed
 * into a {@code Location} header — a callback that honoured a caller-supplied {@code redirect_uri}
 * would be an open redirect attached to a freshly minted session cookie.
 *
 * <h2>State</h2>
 *
 * <p>The nonce is issued into an {@code httpOnly} cookie and into the authorize URL, and the
 * callback accepts nothing that does not match both (ADR 004 Security). Without it the callback is
 * an endpoint that signs a browser into whatever account an attacker's authorization code names.
 * The cookie is cleared on every callback, matched or not, so a {@code state} is genuinely
 * single-use.
 *
 * <p>Whether the round trip links or signs in travels in that same {@code httpOnly} cookie rather
 * than through GitHub as a query parameter, so it cannot be flipped by anyone but us.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth/github")
@RequiresDatabase
public class GithubOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GithubOAuthController.class);

    private static final String LINK_MODE = "link";

    private final OAuthFlowService flow;
    private final OAuthStateCookieFactory states;
    private final SessionCookieFactory cookies;

    public GithubOAuthController(OAuthFlowService flow, OAuthStateCookieFactory states,
            SessionCookieFactory cookies) {
        this.flow = flow;
        this.states = states;
        this.cookies = cookies;
    }

    /**
     * Step 1–2 of PLAN §4.0.5's GitHub table.
     *
     * @param mode {@code link} demands a live session — it is the explicit confirmation ADR 009 §4
     *        requires, and a confirmation from nobody in particular confirms nothing
     */
    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam(defaultValue = "sign_in") String mode,
            @AuthenticationPrincipal UserContext caller) {
        boolean linkMode = LINK_MODE.equalsIgnoreCase(mode);
        if (linkMode && caller == null) {
            throw new UnauthorizedException();
        }
        OAuthState state = OAuthState.issue(linkMode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, states.issue(state).toString())
                .header(HttpHeaders.LOCATION, flow.authorizationUri(state).toString())
                .build();
    }

    /**
     * Step 3–4. Two parameters rather than four: {@code code} and {@code state} are read from the
     * request that already has to be injected for the state cookie, which keeps this within the
     * three-argument limit ({@code AGENTS.md}) without inventing a binding object for two strings.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request,
            @AuthenticationPrincipal UserContext caller) {
        try {
            OAuthState issued = validatedState(request);
            return issued.linkMode()
                    ? completeLink(request.getParameter("code"), caller)
                    : completeSignIn(request.getParameter("code"));
        } catch (DomainException failed) {
            // The provider's own text never reaches the URL: only a code from errors.yaml, which
            // the frontend already knows how to translate.
            log.info("github_oauth_failed — {}", failed.code());
            return redirect(flow.failureRedirect(failed.code()));
        }
    }

    private OAuthState validatedState(HttpServletRequest request) {
        OAuthState issued = states.read(request).orElseThrow(InvalidOAuthStateException::new);
        if (!issued.matches(request.getParameter("state"))) {
            throw new InvalidOAuthStateException();
        }
        return issued;
    }

    private ResponseEntity<Void> completeSignIn(String authorizationCode) {
        ExternalSignIn signIn = flow.signIn(authorizationCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, states.cleared().toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.accessToken(signIn.session().accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.refreshToken(signIn.session().refreshToken()).toString())
                .header(HttpHeaders.LOCATION, flow.appRedirect(signIn.providerLinked()).toString())
                .build();
    }

    /**
     * The session is untouched: the caller is already signed in as the account that just gained a
     * provider, and re-issuing cookies would only invite the question of whose account they are for.
     */
    private ResponseEntity<Void> completeLink(String authorizationCode, UserContext caller) {
        if (caller == null) {
            // The session expired between start and callback. Refusing is the only safe answer —
            // there is no account to attach the identity to, and guessing one would be the hijack.
            throw new UnauthorizedException();
        }
        flow.link(caller.userId(), authorizationCode);
        return redirect(flow.appRedirect(true));
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, states.cleared().toString())
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }
}
