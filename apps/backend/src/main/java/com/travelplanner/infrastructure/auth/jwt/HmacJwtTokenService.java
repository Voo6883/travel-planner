package com.travelplanner.infrastructure.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.travelplanner.application.auth.AccessTokenClaims;
import com.travelplanner.application.auth.JwtTokenService;
import com.travelplanner.config.AuthSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * HS256 access tokens, signed with {@code JWT_SECRET} (ADR 002; claims per ADR 009 §1).
 *
 * <p>Symmetric signing is the right shape here because this system is both the only issuer and the
 * only verifier. An asymmetric key pair buys public verifiability that nothing in the architecture
 * needs, at the cost of key distribution.
 *
 * <h2>Where the key comes from</h2>
 *
 * <p>The secret is read from the environment and is never committed — not to a properties file,
 * not to a test fixture. When it is absent the behaviour depends on where we are:
 *
 * <ul>
 *   <li><b>{@code prod}</b> — startup fails. A production deployment signing with a key nobody
 *       chose is worse than a deployment that does not start.
 *   <li><b>anywhere else</b> — a random key is generated for this process, with a loud warning.
 *       Sessions then do not survive a restart and two instances reject each other's tokens, which
 *       is exactly the annoyance that gets a developer to set the variable. The alternative — a
 *       committed default — is a signing key published to everyone who can read the repository.
 * </ul>
 *
 * <h2>Verification</h2>
 *
 * <p>{@link #parse} pins the algorithm to HS256 before verifying. Accepting whatever the token's
 * own header claims is the classic JWT algorithm-confusion flaw, and {@code alg: none} would make
 * every token self-signing.
 */
@Component
public class HmacJwtTokenService implements JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(HmacJwtTokenService.class);

    /** ADR 009 §1 — the token version claim compared against {@code user.token_version}. */
    static final String TOKEN_VERSION_CLAIM = "tv";

    private final byte[] secret;
    private final String issuer;
    private final java.time.Duration ttl;

    public HmacJwtTokenService(AuthSecurityProperties properties, Environment environment) {
        AuthSecurityProperties.Jwt jwt = properties.getJwt();
        this.secret = resolveSecret(jwt, environment);
        this.issuer = jwt.getIssuer();
        this.ttl = properties.getSession().getAccessTokenTtl();
    }

    @Override
    public String issue(UUID userId, int tokenVersion) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                // A unique id per token, so a security event can name one session without the
                // token itself ever appearing in a log line.
                .jwtID(UUID.randomUUID().toString())
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .build();
        try {
            SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(new MACSigner(secret));
            return signed.serialize();
        } catch (JOSEException unsignable) {
            throw new IllegalStateException("Could not sign the access token", unsignable);
        }
    }

    @Override
    public Optional<AccessTokenClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signed = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(signed.getHeader().getAlgorithm())
                    || !signed.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            return claimsOf(signed.getJWTClaimsSet());
        } catch (ParseException | JOSEException | IllegalArgumentException rejected) {
            // Malformed, tampered, or signed with another key. All the same to a caller: this
            // request has no session.
            return Optional.empty();
        }
    }

    private Optional<AccessTokenClaims> claimsOf(JWTClaimsSet claims) throws ParseException {
        Date expiry = claims.getExpirationTime();
        Date issuedAt = claims.getIssueTime();
        if (expiry == null || issuedAt == null || !issuer.equals(claims.getIssuer())) {
            return Optional.empty();
        }
        if (!expiry.toInstant().isAfter(Instant.now())) {
            return Optional.empty();
        }
        Integer tokenVersion = claims.getIntegerClaim(TOKEN_VERSION_CLAIM);
        if (tokenVersion == null) {
            // A token minted before revocation existed cannot be checked against it, so it is not
            // accepted rather than accepted unchecked.
            return Optional.empty();
        }
        return Optional.of(new AccessTokenClaims(
                UUID.fromString(claims.getSubject()), tokenVersion, issuedAt.toInstant()));
    }

    private static byte[] resolveSecret(AuthSecurityProperties.Jwt jwt, Environment environment) {
        String configured = jwt.getSecret();
        if (configured.length() >= AuthSecurityProperties.Jwt.MINIMUM_SECRET_LENGTH) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("JWT_SECRET must be set to at least "
                    + AuthSecurityProperties.Jwt.MINIMUM_SECRET_LENGTH + " characters in prod");
        }
        log.warn("JWT_SECRET is unset or too short — signing with a key generated for this process. "
                + "Sessions will not survive a restart and separate instances will reject each "
                + "other's tokens. Set JWT_SECRET in .env.");
        byte[] generated = new byte[AuthSecurityProperties.Jwt.MINIMUM_SECRET_LENGTH];
        new SecureRandom().nextBytes(generated);
        return Base64.getEncoder().encode(generated);
    }
}
