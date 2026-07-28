package com.travelplanner.infrastructure.auth.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.exception.ProviderUnavailableException;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import com.travelplanner.infrastructure.auth.github.GithubProfile.GithubEmail;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR 009 §4's GitHub rule — primary <em>and</em> verified, never a {@code noreply} alias — and the
 * authorize URL that carries the {@code state} nonce.
 *
 * <p>The network is stubbed and the selection rule is the subject, for the same reason the Firebase
 * test stubs the signature: CI may not depend on a live provider, and the rule that keeps an
 * unverified address off an account is a pure function of what GitHub returned.
 */
class GithubOAuthIdentityAdapterTest {

    private static final String ACCOUNT_ID = "4242";

    private final StubGithubApi api = new StubGithubApi();
    private final GithubOAuthIdentityAdapter adapter =
            new GithubOAuthIdentityAdapter(api, properties());

    @Test
    void speaksForTheGithubProvider() {
        assertThat(adapter.provider()).isEqualTo(AuthProvider.GITHUB);
    }

    // -------------------------------------------------------------------------------------------
    // The address rule (ADR 009 §4)
    // -------------------------------------------------------------------------------------------

    @Test
    void usesThePrimaryVerifiedAddress() {
        api.profile = profile(
                new GithubEmail("secondary@example.com", false, true),
                new GithubEmail("primary@example.com", true, true));

        IdentityClaims claims = adapter.authenticate(credential());

        assertThat(claims.email()).isEqualTo("primary@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.subject())
                .describedAs("the numeric id, because a login name can be renamed")
                .isEqualTo(ACCOUNT_ID);
    }

    @Test
    void ignoresAnUnverifiedPrimaryAddress() {
        // A string the user typed into GitHub and nobody checked. Accepting it would let anyone
        // claim any address — exactly the door /auth/register's verification mail closes.
        api.profile = profile(new GithubEmail("primary@example.com", true, false));

        assertThat(adapter.authenticate(credential()).email()).isNull();
        assertThat(adapter.authenticate(credential()).emailVerified()).isFalse();
    }

    @Test
    void ignoresAVerifiedButSecondaryAddress() {
        // Picking arbitrarily among several would make which account a user lands in depend on
        // GitHub's array ordering.
        api.profile = profile(new GithubEmail("secondary@example.com", false, true));

        assertThat(adapter.authenticate(credential()).email()).isNull();
    }

    @Test
    void ignoresTheNoreplyAlias() {
        // GitHub's privacy placeholder: routes nowhere, so no verification, password reset, or trip
        // notification could ever reach the person, and it is derived from a mutable username.
        api.profile = profile(new GithubEmail("4242+aisyah@users.noreply.github.com", true, true));

        assertThat(adapter.authenticate(credential()).email()).isNull();
    }

    @Test
    void ignoresTheNoreplyAliasWhateverItsCase() {
        api.profile = profile(new GithubEmail("4242+aisyah@USERS.NOREPLY.GITHUB.COM", true, true));

        assertThat(adapter.authenticate(credential()).email()).isNull();
    }

    @Test
    void stillIdentifiesAnAccountWithNoUsableAddress() {
        // The identity is complete without one: the join key is the subject, and an already-linked
        // account signs in fine. Only creating an account needs an address.
        api.profile = profile();

        IdentityClaims claims = adapter.authenticate(credential());

        assertThat(claims.subject()).isEqualTo(ACCOUNT_ID);
        assertThat(claims.email()).isNull();
    }

    // -------------------------------------------------------------------------------------------
    // Failure classification
    // -------------------------------------------------------------------------------------------

    @Test
    void reportsARejectedCodeAsAFailedCredential() {
        api.failure = new InvalidCredentialsException();

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reportsAnOutageAsAnOutage() {
        api.failure = new ProviderUnavailableException(new IllegalStateException("502"));

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    // -------------------------------------------------------------------------------------------
    // The authorize URL (PLAN §4.0.5 step 2)
    // -------------------------------------------------------------------------------------------

    @Test
    void buildsAnAuthorizeUrlCarryingTheStateNonceAndNoSecret() {
        URI authorize = adapter.authorizationUri("nonce-123");

        assertThat(authorize.toString())
                .startsWith("https://github.com/login/oauth/authorize")
                .contains("client_id=client-id")
                .contains("state=nonce-123")
                .contains("scope=user:email");
        // ADR 004: no GitHub secret in the browser, and the authorize URL is the browser's.
        assertThat(authorize.toString()).doesNotContain("secret");
    }

    private static ProviderCredential credential() {
        return new ProviderCredential(null, "an-authorization-code");
    }

    private static GithubProfile profile(GithubEmail... emails) {
        return new GithubProfile(ACCOUNT_ID, List.of(emails));
    }

    private static IdentityProviderProperties properties() {
        IdentityProviderProperties properties = new IdentityProviderProperties();
        properties.getGithub().setClientId("client-id");
        properties.getGithub().setClientSecret("client-secret");
        return properties;
    }

    /** Stands in for the three HTTP calls, which is everything CI may not depend on. */
    private static final class StubGithubApi implements GithubApi {

        private GithubProfile profile;
        private RuntimeException failure;

        @Override
        public GithubProfile exchange(String authorizationCode) {
            if (failure != null) {
                throw failure;
            }
            return profile;
        }
    }
}
