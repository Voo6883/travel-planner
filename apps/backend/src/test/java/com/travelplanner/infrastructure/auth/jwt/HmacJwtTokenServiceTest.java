package com.travelplanner.infrastructure.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.auth.AccessTokenClaims;
import com.travelplanner.config.AuthSecurityProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The access token: what it carries (ADR 009 §1), and what it refuses to accept back. */
class HmacJwtTokenServiceTest {

    private static final String SECRET = "a-test-signing-key-that-is-long-enough-32";

    private final HmacJwtTokenService tokens = serviceWith(SECRET);

    @Test
    void issuesATokenThatParsesBackToTheSameSubjectAndTokenVersion() {
        UUID userId = UUID.randomUUID();

        AccessTokenClaims claims = tokens.parse(tokens.issue(userId, 7)).orElseThrow();

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tokenVersion()).isEqualTo(7);
        assertThat(claims.issuedAt()).isNotNull();
    }

    @Test
    void carriesNeitherEmailNorRoleNorVerificationState() {
        // ADR 009 §2 makes user state authoritative per request. A token that carried these would
        // keep asserting the old values for up to its full 30-minute lifetime.
        String token = tokens.issue(UUID.randomUUID(), 0);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(payload).doesNotContain("email").doesNotContain("role").doesNotContain("verified");
        assertThat(payload).contains("\"tv\":0").contains("\"sub\"").contains("\"iat\"");
    }

    @Test
    void expiresAtTheThirtyMinuteLifetimeAdr009Locks() {
        AuthSecurityProperties properties = propertiesWith(SECRET);

        assertThat(properties.getSession().getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rejectsAnExpiredToken() {
        AuthSecurityProperties expiringImmediately = propertiesWith(SECRET);
        expiringImmediately.getSession().setAccessTokenTtl(Duration.ofSeconds(-1));
        HmacJwtTokenService shortLived =
                new HmacJwtTokenService(expiringImmediately, new MockEnvironment());

        assertThat(shortLived.parse(shortLived.issue(UUID.randomUUID(), 0))).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        HmacJwtTokenService other = serviceWith("a-completely-different-key-also-32-chars");

        assertThat(tokens.parse(other.issue(UUID.randomUUID(), 0))).isEmpty();
    }

    @Test
    void rejectsATamperedPayload() {
        String token = tokens.issue(UUID.randomUUID(), 0);
        String[] parts = token.split("\\.");
        String forged = new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"tv\":0", "\"tv\":9");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes()) + "."
                + parts[2];

        assertThat(tokens.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsAnUnsignedTokenRatherThanTrustingItsOwnAlgorithmHeader() {
        // The classic JWT algorithm-confusion flaw: `alg: none` makes every token self-signing.
        String unsigned = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes())
                + "." + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("{\"sub\":\"x\",\"tv\":0}".getBytes()) + ".";

        assertThat(tokens.parse(unsigned)).isEmpty();
    }

    @Test
    void rejectsGarbageAndNothingAtAllWithoutThrowing() {
        // A filter cannot throw — an exception there never reaches GlobalExceptionHandler and the
        // caller would get a container error page instead of the contract's envelope.
        assertThat(tokens.parse(null)).isEqualTo(Optional.empty());
        assertThat(tokens.parse("")).isEmpty();
        assertThat(tokens.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesToStartInProductionWithNoSigningKey() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new HmacJwtTokenService(propertiesWith(""), production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void generatesAKeyOutsideProductionSoNoSecretHasToBeCommitted() {
        HmacJwtTokenService withoutSecret =
                new HmacJwtTokenService(propertiesWith(""), new MockEnvironment());

        assertThat(withoutSecret.parse(withoutSecret.issue(UUID.randomUUID(), 1))).isPresent();
        // ...and a token from a different process does not verify, which is the visible cost that
        // pushes a developer to set the variable.
        assertThat(withoutSecret.parse(tokens.issue(UUID.randomUUID(), 1))).isEmpty();
    }

    private static HmacJwtTokenService serviceWith(String secret) {
        return new HmacJwtTokenService(propertiesWith(secret), new MockEnvironment());
    }

    private static AuthSecurityProperties propertiesWith(String secret) {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getJwt().setSecret(secret);
        return properties;
    }
}
