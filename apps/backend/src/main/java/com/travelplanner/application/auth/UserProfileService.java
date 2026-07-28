package com.travelplanner.application.auth;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the current account for {@code GET /auth/me} (UC-A11) and for every sign-in response.
 *
 * <p>Read-only and always from the database. It would be cheaper to build the response from the
 * {@code UserContext} the filter already assembled, but that context is itself a per-request read
 * (ADR 009 §2) and does not carry the username or the linked-provider list — so the cheap version
 * would either be wrong or would push those fields into the token, which is exactly what ADR 009
 * §2 rules out.
 */
@Service
@RequiresDatabase
public class UserProfileService {

    private final UserRepositoryPort users;
    private final UserIdentityRepositoryPort identities;

    public UserProfileService(UserRepositoryPort users, UserIdentityRepositoryPort identities) {
        this.users = users;
        this.identities = identities;
    }

    /**
     * @throws UnauthorizedException when the account has disappeared between authentication and
     *         this read — a deleted account must not render a profile from a still-valid cookie
     */
    @Transactional(readOnly = true)
    public CurrentUserView load(UUID userId) {
        User user = users.findById(userId).orElseThrow(UnauthorizedException::new);
        return CurrentUserView.of(user, identities.findAllByUserId(userId));
    }
}
