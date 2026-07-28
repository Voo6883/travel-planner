package com.travelplanner.infrastructure.auth.local;

import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.port.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt, at the strength PLAN §4.0.9 requires (10 or above; the configured default is 12).
 *
 * <p>The cost parameter is stored inside every hash, so raising it later does not invalidate
 * existing passwords — old hashes keep verifying at their original cost and are re-encoded at the
 * new one when their owner next signs in (task 09 owns that re-encode, if it is ever wanted).
 */
@Component
public class BcryptPasswordHasher implements PasswordHasherPort {

    /**
     * A syntactically valid BCrypt hash of a value nobody knows, used only as a comparison target
     * when an account has no password. Verifying against it costs the same as verifying against a
     * real hash, which is the point: skipping the comparison would make an OAuth-only account —
     * and therefore a registered address — detectable by how fast it fails (ADR 009 §4).
     */
    private final String absentPasswordPlaceholder;

    private final BCryptPasswordEncoder encoder;

    public BcryptPasswordHasher(AuthSecurityProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.getPassword().getBcryptStrength());
        this.absentPasswordPlaceholder = encoder.encode("no-password-is-set-for-this-account");
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null) {
            return false;
        }
        if (storedHash == null || storedHash.isBlank()) {
            // Deliberately still hashes. The result is discarded; the elapsed time is not.
            encoder.matches(rawPassword, absentPasswordPlaceholder);
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }
}
