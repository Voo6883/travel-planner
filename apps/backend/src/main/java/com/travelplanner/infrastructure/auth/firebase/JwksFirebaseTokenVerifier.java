package com.travelplanner.infrastructure.auth.firebase;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.exception.InvalidProviderTokenException;
import com.travelplanner.domain.exception.ProviderUnavailableException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies Firebase ID tokens against Google's published signing keys.
 *
 * <h2>Why Nimbus and not the Firebase Admin SDK</h2>
 *
 * <p>PLAN §4.0.5 sketches {@code FirebaseIdentityAdapter} using the Admin SDK. Verification is the
 * one Admin SDK operation that needs no service-account credential — a Firebase ID token is an
 * ordinary RS256 JWT and Google publishes the public half of the key set — and this project already
 * carries {@code nimbus-jose-jwt} for its own access tokens. Using it here adds no dependency, no
 * transitive tree, and no {@code GOOGLE_APPLICATION_CREDENTIALS} requirement in CI, while doing
 * precisely what the SDK's {@code verifyIdToken} does. <b>Reported as a deviation from PLAN
 * §4.0.5's wording</b> rather than decided quietly; the placement rule that ADR 004 actually locks
 * — vendor verification only inside {@code infrastructure/auth/firebase/} — is unchanged.
 *
 * <h2>Signature only</h2>
 *
 * <p>{@code aud}, {@code iss}, and {@code firebase.sign_in_provider} are deliberately <em>not</em>
 * checked here. They are the ADR 009 §4 policy, they live in {@link FirebaseIdentityAdapter}, and
 * keeping them out of the key-selection path is what makes them testable without a network.
 *
 * <p>The algorithm is pinned to RS256 before anything is verified. Accepting whatever the token's
 * own header claims is the classic JWT algorithm-confusion flaw, and {@code alg: none} would make
 * every token self-signing — the same reason {@code HmacJwtTokenService} pins HS256.
 *
 * <p>The key set is cached and refreshed by Nimbus, so a sign-in is not one HTTP round trip to
 * Google per request; a cache miss on an unknown {@code kid} triggers a rate-limited refetch.
 */
public class JwksFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwksFirebaseTokenVerifier.class);

    /** Firebase's own nested claim object: {@code {"firebase": {"sign_in_provider": "google.com"}}}. */
    private static final String FIREBASE_CLAIM = "firebase";
    private static final String SIGN_IN_PROVIDER_CLAIM = "sign_in_provider";
    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final DefaultJWTProcessor<SecurityContext> processor;

    public JwksFirebaseTokenVerifier(IdentityProviderProperties.Firebase properties) {
        this.processor = buildProcessor(properties.getJwksUri());
        log.info("Firebase ID tokens will be verified against {}", properties.getJwksUri());
    }

    @Override
    public FirebaseIdToken verify(String idToken) {
        try {
            return read(processor.process(idToken, null));
        } catch (RemoteKeySourceException unreachable) {
            // Google's key set could not be fetched. Reporting this as a bad token would let an
            // outage look like "no such identity", and a retry would then create a second account.
            throw new ProviderUnavailableException(unreachable);
        } catch (ParseException | BadJOSEException | JOSEException | IllegalArgumentException bad) {
            log.warn("firebase_token_rejected — signature, structure, or lifetime");
            throw new InvalidProviderTokenException();
        }
    }

    private static FirebaseIdToken read(JWTClaimsSet claims) throws ParseException {
        return new FirebaseIdToken(
                claims.getAudience().isEmpty() ? null : claims.getAudience().get(0),
                claims.getIssuer(),
                claims.getSubject(),
                claims.getStringClaim(EMAIL_CLAIM),
                Boolean.TRUE.equals(claims.getBooleanClaim(EMAIL_VERIFIED_CLAIM)),
                signInProviderOf(claims));
    }

    /** Absent, malformed, or non-string all read as {@code null}, which the adapter then refuses. */
    private static String signInProviderOf(JWTClaimsSet claims) {
        Object firebase = claims.getClaim(FIREBASE_CLAIM);
        if (!(firebase instanceof Map<?, ?> attributes)) {
            return null;
        }
        Object signInProvider = attributes.get(SIGN_IN_PROVIDER_CLAIM);
        return signInProvider instanceof String value ? value : null;
    }

    private static DefaultJWTProcessor<SecurityContext> buildProcessor(String jwksUri) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource(jwksUri)));
        // `exp` is enforced by the default verifier; requiring `sub` and `iat` as well means a token
        // that is missing the identity it is supposed to assert is rejected rather than mapped to
        // nulls further down.
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().build(), Set.of("sub", "iat", "exp")));
        return processor;
    }

    private static JWKSource<SecurityContext> keySource(String jwksUri) {
        try {
            return JWKSourceBuilder.create(URI.create(jwksUri).toURL()).retrying(true).build();
        } catch (MalformedURLException | IllegalArgumentException misconfigured) {
            throw new IllegalStateException(
                    "travelplanner.identity.firebase.jwks-uri is not a URL: " + jwksUri, misconfigured);
        }
    }
}
