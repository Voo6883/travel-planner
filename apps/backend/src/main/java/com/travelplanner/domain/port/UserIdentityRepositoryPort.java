package com.travelplanner.domain.port;

import com.travelplanner.domain.model.UserIdentity;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link UserIdentity}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Narrow on purpose. Task 08 creates the {@code LOCAL} row at registration and reads a user's
 * providers for {@code GET /auth/me} (UC-A11). The provider-subject lookup and the unlink rules
 * that ADR 009 §4 governs belong to task 10 and extend this port then, rather than being guessed
 * at now.
 */
public interface UserIdentityRepositoryPort {

    UserIdentity save(UserIdentity identity);

    List<UserIdentity> findAllByUserId(UUID userId);
}
