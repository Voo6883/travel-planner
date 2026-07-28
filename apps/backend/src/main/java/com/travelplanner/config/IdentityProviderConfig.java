package com.travelplanner.config;

import com.travelplanner.infrastructure.auth.firebase.DevFirebaseTokenVerifier;
import com.travelplanner.infrastructure.auth.firebase.FirebaseTokenVerifier;
import com.travelplanner.infrastructure.auth.firebase.JwksFirebaseTokenVerifier;
import com.travelplanner.infrastructure.auth.github.DevGithubApi;
import com.travelplanner.infrastructure.auth.github.GithubApi;
import com.travelplanner.infrastructure.auth.github.HttpGithubApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chooses the real or the development implementation of each external provider's network half
 * (§4.0.7, ADR 004).
 *
 * <p><strong>A plain {@code if} rather than {@code @ConditionalOnProperty}.</strong> The condition
 * is "a credential was configured", and an empty-string default — which is what
 * {@code ${FIREBASE_PROJECT_ID:}} produces — makes the property *present* and therefore matches a
 * naive {@code @ConditionalOnProperty}. Expressing it in Java makes the rule readable and its
 * inverse impossible to get wrong; the two variants are then genuinely exclusive rather than
 * exclusive by annotation ordering.
 *
 * <p>Only the network half is swapped. {@code FirebaseIdentityAdapter} and
 * {@code GithubOAuthIdentityAdapter} are ordinary components in every configuration, so the
 * ADR 009 §4 rules they enforce — {@code aud}, {@code firebase.sign_in_provider}, primary-and-
 * verified email — are the same code in development, in CI, and in production. A stub that also
 * stubbed the rules would prove nothing.
 *
 * <p>Both development variants refuse to be constructed under the {@code prod} profile, so
 * "somebody forgot to set the environment variables" is a failed startup rather than a system that
 * quietly accepts unverified identities.
 */
@Configuration
@EnableConfigurationProperties(IdentityProviderProperties.class)
public class IdentityProviderConfig {

    @Bean
    public FirebaseTokenVerifier firebaseTokenVerifier(IdentityProviderProperties properties,
            Environment environment) {
        return properties.getFirebase().isConfigured()
                ? new JwksFirebaseTokenVerifier(properties.getFirebase())
                : DevFirebaseTokenVerifier.create(environment);
    }

    @Bean
    public GithubApi githubApi(IdentityProviderProperties properties, Environment environment) {
        return properties.getGithub().isConfigured()
                ? new HttpGithubApi(properties.getGithub())
                : DevGithubApi.create(environment);
    }
}
