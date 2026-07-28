package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.UserEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Matches {@code ux_user_username_lower}. */
    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * ADR 009 §1 revocation, as one atomic statement.
     *
     * <p>Written as an increment in SQL rather than a read-modify-write in Java on purpose. Two
     * concurrent revocations — an administrator disabling an account while its owner logs out of
     * all devices — would otherwise both read the same version, both write {@code version + 1},
     * and one revocation would be silently lost.
     *
     * <p>{@code clearAutomatically} because this bypasses the persistence context; without it a
     * user entity loaded earlier in the same transaction would still report the old version.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity user set user.tokenVersion = user.tokenVersion + 1, "
            + "user.sessionsValidAfter = :validAfter, user.updatedAt = :validAfter "
            + "where user.id = :userId")
    int revokeSessions(@Param("userId") UUID userId, @Param("validAfter") Instant validAfter);
}
