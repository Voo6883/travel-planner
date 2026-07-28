package com.travelplanner.domain.port;

/**
 * One-way password hashing (PLAN §4.0.9: BCrypt, strength 10+). Implemented in
 * {@code infrastructure/auth/local/}.
 *
 * <p>A port rather than a direct call into Spring Security's {@code PasswordEncoder} for two
 * reasons: the algorithm is a security decision that should be replaceable without touching a
 * service, and unit tests of the registration and login flows must not pay for a real BCrypt round
 * per assertion.
 */
public interface PasswordHasherPort {

    /** @return the encoded hash, including its algorithm identifier, salt, and cost parameter */
    String hash(String rawPassword);

    /**
     * Constant-time comparison of a candidate against a stored hash.
     *
     * <p>Implementations must tolerate a {@code null} stored hash — an OAuth-only account has none
     * (ADR 009 §4) — and must still consume comparable time when it is absent, so that response
     * timing does not reveal which accounts have local passwords.
     */
    boolean matches(String rawPassword, String storedHash);
}
