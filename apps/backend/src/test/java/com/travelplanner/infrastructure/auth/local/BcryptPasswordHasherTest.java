package com.travelplanner.infrastructure.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.config.AuthSecurityProperties;
import org.junit.jupiter.api.Test;

/**
 * The real hasher. Kept to a handful of assertions on purpose — every one of them pays a full
 * BCrypt key-stretching round, which is the property being verified.
 */
class BcryptPasswordHasherTest {

    private final AuthSecurityProperties properties = properties();
    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(properties);

    @Test
    void producesABcryptHashAtTheConfiguredCostAndNeverThePasswordItself() {
        String hash = hasher.hash("correct-horse-battery");

        // $2a$04$ — algorithm and cost are stored inside the hash, which is what lets the strength
        // be raised later without invalidating existing passwords.
        assertThat(hash).startsWith("$2a$04$").doesNotContain("correct-horse-battery");
        assertThat(hasher.matches("correct-horse-battery", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void saltsEachHashSoTwoIdenticalPasswordsDoNotLookIdentical() {
        // Without a per-password salt, a leaked table reveals which accounts share a password.
        assertThat(hasher.hash("same-password")).isNotEqualTo(hasher.hash("same-password"));
    }

    @Test
    void refusesAnAccountWithNoLocalPasswordWithoutSkippingTheComparison() {
        // ADR 009 §4: password_hash IS NULL marks an OAuth-only account. Returning early would
        // make those accounts — and therefore registered addresses — detectable by response time.
        assertThat(hasher.matches("anything", null)).isFalse();
        assertThat(hasher.matches("anything", "")).isFalse();
        assertThat(hasher.matches(null, hasher.hash("x"))).isFalse();
    }

    /** Cost 4 — the BCrypt minimum. The production default is 12 (PLAN §4.0.9 requires 10+). */
    private static AuthSecurityProperties properties() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getPassword().setBcryptStrength(4);
        return properties;
    }
}
