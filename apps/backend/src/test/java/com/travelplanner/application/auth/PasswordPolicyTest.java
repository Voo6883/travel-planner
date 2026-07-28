package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.port.PasswordHasherPort;
import org.junit.jupiter.api.Test;

/** PLAN §4.0.9 password rules, and the guarantee that a hash implies a validated password. */
class PasswordPolicyTest {

    private final RecordingHasher hasher = new RecordingHasher();
    private final PasswordPolicy policy = new PasswordPolicy(hasher, new AuthSecurityProperties());

    @Test
    void acceptsThePlannedMinimumOfEightCharacters() {
        policy.validate("12345678");
    }

    @Test
    void rejectsAPasswordShorterThanThePlannedMinimum() {
        assertThatThrownBy(() -> policy.validate("1234567"))
                .isInstanceOf(ValidationFailedException.class)
                .extracting(thrown -> ((ValidationFailedException) thrown).details())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("fields");
    }

    @Test
    void rejectsBlankAndNull() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> policy.validate("   ")).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsAPasswordLongerThanBcryptActuallyReads() {
        // BCrypt hashes the first 72 bytes. Accepting more would silently authenticate a
        // different password than the user chose, which is worse than rejecting the input.
        assertThatThrownBy(() -> policy.validate("x".repeat(73)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void reportsFailuresAgainstTheWireFieldNameSoAFormCanHighlightIt() {
        ValidationFailedException failure =
                (ValidationFailedException) catchOf(() -> policy.validate("short"));

        assertThat(failure.details()).containsKey("fields");
        assertThat(failure.details().get("fields").toString()).contains("password");
    }

    @Test
    void neverHashesAPasswordThatWasNotValidatedFirst() {
        assertThatThrownBy(() -> policy.encode("short")).isInstanceOf(ValidationFailedException.class);
        assertThat(hasher.hashed).isNull();

        assertThat(policy.encode("long-enough-password")).isEqualTo("hash:long-enough-password");
        assertThat(hasher.hashed).isEqualTo("long-enough-password");
    }

    private static Throwable catchOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected a failure");
        } catch (RuntimeException thrown) {
            return thrown;
        }
    }

    /** Real BCrypt would make this suite pay a key-stretching round per assertion. */
    private static final class RecordingHasher implements PasswordHasherPort {

        private String hashed;

        @Override
        public String hash(String rawPassword) {
            hashed = rawPassword;
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return ("hash:" + rawPassword).equals(storedHash);
        }
    }
}
