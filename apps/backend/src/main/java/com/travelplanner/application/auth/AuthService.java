package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.User;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The single use-case entry point for authentication (PLAN §4.0.5 names this file).
 *
 * <p>Orchestration only — no rule is decided here. Registration policy is
 * {@link RegistrationService}, credential and lockout policy is {@link AuthenticationService}, and
 * token lifetime and revocation are {@link SessionService}. Keeping this class free of logic is
 * what lets task 10 add {@code POST /auth/firebase} and the GitHub callback as siblings of
 * {@link #login} rather than as edits inside it.
 *
 * <p>Not transactional. Each collaborator declares its own boundary, and a transaction spanning a
 * whole sign-in would roll back the failed-attempt counter every time a sign-in failed — which is
 * to say, exactly when the counter matters.
 */
@Service
@RequiresDatabase
public class AuthService {

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final SessionService sessions;

    public AuthService(RegistrationService registration, AuthenticationService authentication,
            SessionService sessions) {
        this.registration = registration;
        this.authentication = authentication;
        this.sessions = sessions;
    }

    /**
     * UC-A01. Returns nothing on purpose — the response is identical whether or not the address
     * was already registered (ADR 009 §6).
     */
    public void register(RegisterCommand command) {
        registration.register(command);
    }

    /** UC-A04. Email or username, plus password. */
    public IssuedSession login(LoginCommand command, String clientIp) {
        User user = authentication.authenticate(command, clientIp);
        return sessions.issueFor(user);
    }

    /** ADR 009 §3. Rotates the presented refresh token and mints a fresh pair. */
    public IssuedSession refresh(String rawRefreshToken) {
        return sessions.refresh(rawRefreshToken);
    }

    /**
     * UC-A10. Idempotent: signing out twice, or with no session at all, is a success. A logout
     * that can fail is a logout users learn to skip.
     */
    public void logout(String rawRefreshToken) {
        sessions.endSession(rawRefreshToken);
    }

    /** ADR 009 §5 — {@code POST /auth/logout-all}. */
    public void logoutAll(UUID userId) {
        sessions.endAllSessions(userId);
    }
}
