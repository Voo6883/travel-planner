package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code "user"}. Package-visible to services only through
 * {@code UserRepositoryAdapter} — PLAN §4.0.2-H forbids repositories in {@code application/}.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Case-insensitive to match {@code ux_user_email_lower}. A case-sensitive lookup would miss the
     * row it is about to collide with, turning "email already registered" into a constraint
     * violation stack trace instead of a typed error.
     */
    Optional<UserEntity> findByEmailIgnoreCase(String email);
}
