package com.travelplanner.infrastructure.auth.firebase;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.travelplanner.domain.exception.InvalidProviderTokenException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * The development stand-in for {@link JwksFirebaseTokenVerifier} (§4.0.7 stub-adapter rule, and the
 * same shape as {@code StubMailerAdapter}).
 *
 * <p><strong>It does not check the signature.</strong> That is the entire point and the entire
 * danger, so it is fenced in three ways:
 *
 * <ul>
 *   <li>it is only ever constructed when {@code travelplanner.identity.firebase.project-id} is
 *       unset, so configuring a real project replaces it;
 *   <li>{@link #create} <em>throws at startup</em> under the {@code prod} profile — the same
 *       treatment {@code HmacJwtTokenService} gives a missing {@code JWT_SECRET}, and for the same
 *       reason: a production deployment that authenticates unsigned tokens is worse than one that
 *       does not start;
 *   <li>it warns at startup and on every use, so an environment running on it cannot do so quietly.
 * </ul>
 *
 * <p>What it buys: {@code docker compose up} and {@code ./gradlew test} both work with no Firebase
 * project, which is task 10's "do not rely on a live provider in CI". The ADR 009 §4 assertions are
 * unaffected — {@code aud} and {@code firebase.sign_in_provider} are checked by
 * {@link FirebaseIdentityAdapter} against the same configuration either way, so a token that would
 * be refused in production is refused here too.
 *
 * <p>{@code exp} is still enforced. A stub that accepted expired tokens would let a developer chase
 * a bug that production does not have.
 */
public class DevFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DevFirebaseTokenVerifier.class);

    private static final String FIREBASE_CLAIM = "firebase";
    private static final String SIGN_IN_PROVIDER_CLAIM = "sign_in_provider";

    private DevFirebaseTokenVerifier() {
    }

    /**
     * @throws IllegalStateException under {@code prod} — see the class javadoc
     */
    public static DevFirebaseTokenVerifier create(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID must be set in prod — refusing to "
                    + "accept Firebase ID tokens without verifying their signature");
        }
        log.warn("FIREBASE_PROJECT_ID is unset — Firebase ID tokens will NOT have their signatures "
                + "verified. Development only; the application refuses to start this way in prod.");
        return new DevFirebaseTokenVerifier();
    }

    @Override
    public FirebaseIdToken verify(String idToken) {
        log.warn("Accepting an UNVERIFIED Firebase ID token — development configuration");
        JWTClaimsSet claims = parse(idToken);
        requireUnexpired(claims);
        return new FirebaseIdToken(
                claims.getAudience().isEmpty() ? null : claims.getAudience().get(0),
                claims.getIssuer(),
                claims.getSubject(),
                stringClaim(claims, "email"),
                Boolean.TRUE.equals(booleanClaim(claims)),
                signInProviderOf(claims));
    }

    private static JWTClaimsSet parse(String idToken) {
        try {
            JWT token = JWTParser.parse(idToken);
            JWTClaimsSet claims = token.getJWTClaimsSet();
            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new InvalidProviderTokenException();
            }
            return claims;
        } catch (ParseException | IllegalArgumentException | NullPointerException malformed) {
            throw new InvalidProviderTokenException();
        }
    }

    private static void requireUnexpired(JWTClaimsSet claims) {
        Date expiry = claims.getExpirationTime();
        if (expiry == null || !expiry.toInstant().isAfter(Instant.now())) {
            throw new InvalidProviderTokenException();
        }
    }

    private static String signInProviderOf(JWTClaimsSet claims) {
        Object firebase = claims.getClaim(FIREBASE_CLAIM);
        if (!(firebase instanceof Map<?, ?> attributes)) {
            return null;
        }
        Object signInProvider = attributes.get(SIGN_IN_PROVIDER_CLAIM);
        return signInProvider instanceof String value ? value : null;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        return value instanceof String text ? text : null;
    }

    private static Boolean booleanClaim(JWTClaimsSet claims) {
        Object value = claims.getClaim("email_verified");
        return value instanceof Boolean flag ? flag : Boolean.FALSE;
    }
}
