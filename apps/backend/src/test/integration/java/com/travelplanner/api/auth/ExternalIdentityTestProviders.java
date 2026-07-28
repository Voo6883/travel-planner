package com.travelplanner.api.auth;

import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.infrastructure.auth.firebase.FirebaseIdToken;
import com.travelplanner.infrastructure.auth.firebase.FirebaseTokenVerifier;
import com.travelplanner.infrastructure.auth.github.GithubApi;
import com.travelplanner.infrastructure.auth.github.GithubProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The provider network, replaced — and <strong>only</strong> the network.
 *
 * <p>Task 10 forbids CI from depending on a live Firebase project or GitHub OAuth app, so these two
 * beans stand in for the calls that would leave the machine: verifying a Google signature, and
 * exchanging an authorization code. Everything the suite is actually testing runs unchanged —
 * {@code FirebaseIdentityAdapter}'s {@code aud} and {@code sign_in_provider} assertions,
 * {@code GithubOAuthIdentityAdapter}'s primary-and-verified address rule, and every ADR 009 §4
 * linking decision behind them.
 *
 * <p>{@code @Primary} rather than a bean-definition override: {@code IdentityProviderConfig} still
 * declares its own, so the production wiring is exercised at context startup and a change that broke
 * it would still fail here.
 */
@TestConfiguration
public class ExternalIdentityTestProviders {

    @Bean
    @Primary
    public FirebaseTokenVerifier fakeFirebaseTokenVerifier() {
        return new FakeFirebaseTokenVerifier();
    }

    @Bean
    @Primary
    public GithubApi fakeGithubApi() {
        return new FakeGithubApi();
    }

    /** Answers with whatever claims a test registered for a token string. */
    public static final class FakeFirebaseTokenVerifier implements FirebaseTokenVerifier {

        private final Map<String, FirebaseIdToken> tokens = new LinkedHashMap<>();

        /** Set to make every verification fail — the provider-outage case. */
        public RuntimeException failure;

        public String register(String token, FirebaseIdToken claims) {
            tokens.put(token, claims);
            return token;
        }

        public void reset() {
            tokens.clear();
            failure = null;
        }

        @Override
        public FirebaseIdToken verify(String idToken) {
            if (failure != null) {
                throw failure;
            }
            FirebaseIdToken claims = tokens.get(idToken);
            if (claims == null) {
                // What a bad signature looks like from the adapter's side.
                throw new com.travelplanner.domain.exception.InvalidProviderTokenException();
            }
            return claims;
        }
    }

    /** Answers with whatever profile a test registered for an authorization code. */
    public static final class FakeGithubApi implements GithubApi {

        private final Map<String, GithubProfile> profiles = new LinkedHashMap<>();

        public RuntimeException failure;

        public String register(String code, GithubProfile profile) {
            profiles.put(code, profile);
            return code;
        }

        public void reset() {
            profiles.clear();
            failure = null;
        }

        @Override
        public GithubProfile exchange(String authorizationCode) {
            if (failure != null) {
                throw failure;
            }
            GithubProfile profile = profiles.get(authorizationCode);
            if (profile == null) {
                throw new InvalidCredentialsException();
            }
            return profile;
        }
    }
}
