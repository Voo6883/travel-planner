package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.model.UserIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link UserIdentity}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Narrow on purpose. Task 08 created the {@code LOCAL} row at registration and read a user's
 * providers for {@code GET /auth/me} (UC-A11), and deliberately left the provider-subject lookup
 * and the unlink rules to task 10 rather than guessing at them. This is that extension.
 */
public interface UserIdentityRepositoryPort {

    UserIdentity save(UserIdentity identity);

    List<UserIdentity> findAllByUserId(UUID userId);

    /**
     * The identity join key (ADR 009 §4). Emails are reassigned, forwarded, and reused; a provider
     * subject is stable for the life of the provider account, which is why every sign-in resolves
     * through this method and never through the address.
     *
     * <p>Returning at most one row is a schema guarantee, not a convention:
     * {@code ux_user_identity_provider_subject} makes "one external identity, one user" true in the
     * database rather than only in the service that usually checks.
     */
    Optional<UserIdentity> findByProviderAndSubject(AuthProvider provider, String providerSubjectId);

    /**
     * Removes one linked provider (ADR 009 §4). The caller is responsible for refusing to strand an
     * account and for revoking sessions afterwards — see {@code ProviderUnlinkService}; a port
     * cannot enforce a rule that depends on the account's password state.
     */
    void delete(UserIdentity identity);
}
