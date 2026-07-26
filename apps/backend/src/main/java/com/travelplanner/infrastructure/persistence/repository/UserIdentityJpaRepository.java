package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.infrastructure.persistence.entity.UserIdentityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code user_identity}.
 *
 * <p>The linking policy that decides <em>whether</em> a provider may attach to an existing account
 * (ADR 009 §4) is task 10's; these are the two lookups it will need.
 */
public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityEntity, UUID> {

    /** The identity join key — provider subject, never email (ADR 009 §4). */
    Optional<UserIdentityEntity> findByProviderAndProviderSubjectId(AuthProvider provider, String subjectId);

    List<UserIdentityEntity> findAllByUserId(UUID userId);
}
