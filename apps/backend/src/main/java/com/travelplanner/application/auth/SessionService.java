package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues and ends sessions. Every provider — local now, Firebase and GitHub in task 10 — arrives
 * here once its adapter has verified the caller, so the session model is written once (PLAN
 * §4.0.5: "all paths issue the same self-issued JWT session").
 *
 * <p>A session is a pair: a 30-minute access token and a 14-day rotating refresh token
 * (ADR 009 §3). They are issued together and cleared together, because either one alone is a
 * broken half — an access token with no refresh forces a re-login every half hour, and a refresh
 * token with no access token authenticates nothing.
 */
@Service
@RequiresDatabase
public class SessionService {

    private final JwtTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final UserRepositoryPort users;

    public SessionService(JwtTokenService accessTokens, RefreshTokenService refreshTokens,
            UserRepositoryPort users) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    /**
     * The access token freezes {@code token_version} at this instant; any later bump makes it
     * unusable on its next request (ADR 009 §1).
     */
    public IssuedSession issueFor(User user) {
        return new IssuedSession(user.id(),
                accessTokens.issue(user.id(), user.tokenVersion()),
                refreshTokens.issue(user.id()));
    }

    /**
     * Exchanges a refresh token for a new pair, rotating the old one (ADR 009 §3).
     *
     * <p>The account is re-read rather than trusted: fourteen days is long enough for it to have
     * been disabled, and refresh is the one moment a disabled account would otherwise be handed a
     * brand-new 30-minute token.
     *
     * <p>Email verification is deliberately not re-checked. An unverified account can never have
     * obtained a session in the first place (see {@link AuthenticationService}), so the only thing
     * a second check could do is sign out somebody who verified after logging in.
     */
    public IssuedSession refresh(String rawRefreshToken) {
        UUID userId = refreshTokens.rotate(rawRefreshToken);
        User user = users.findById(userId).orElseThrow(UnauthorizedException::new);
        if (!user.enabled()) {
            throw new UnauthorizedException();
        }
        return issueFor(user);
    }

    /**
     * Ends one session. The cookies are cleared by the controller; this is the half that makes it
     * revocation rather than a suggestion — without it the refresh token in a stolen cookie jar
     * keeps working for the rest of its fourteen days (ADR 009 §3).
     */
    public void endSession(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    /** {@code POST /auth/logout-all} (ADR 009 §5) — every device, immediately. */
    public void endAllSessions(UUID userId) {
        refreshTokens.revokeAllSessions(userId, SessionRevocationReason.LOGOUT_ALL);
    }
}
