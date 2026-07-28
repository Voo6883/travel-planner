package com.travelplanner.infrastructure.auth.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.InvalidProviderTokenException;
import com.travelplanner.domain.exception.ProviderEmailNotVerifiedException;
import com.travelplanner.domain.exception.ProviderUnavailableException;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import org.junit.jupiter.api.Test;

/**
 * The two ADR 009 §4 assertions the Firebase path rests on, plus the address rule PLAN §4.0.5
 * names.
 *
 * <p>The signature check is stubbed out and the <em>policy</em> is the subject. That split is the
 * point of {@link FirebaseTokenVerifier}: verifying a real Google signature would need a network, a
 * Firebase project, and a live token, none of which CI may depend on — while the decisions that
 * actually keep an attacker out are pure functions of the claims.
 */
class FirebaseIdentityAdapterTest {

    private static final String PROJECT_ID = "travel-planner-test";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;
    private static final String UID = "firebase-uid-1";
    private static final String EMAIL = "aisyah@example.com";

    private final StubVerifier verifier = new StubVerifier();
    private final FirebaseIdentityAdapter adapter =
            new FirebaseIdentityAdapter(verifier, properties());

    @Test
    void speaksForTheFirebaseGoogleProvider() {
        // What the registry routes on. Without it the adapter is unreachable.
        assertThat(adapter.provider()).isEqualTo(AuthProvider.FIREBASE_GOOGLE);
    }

    @Test
    void acceptsAGoogleSignInForThisProject() {
        verifier.token = token(PROJECT_ID, ISSUER, "google.com", true);

        IdentityClaims claims = adapter.authenticate(credential());

        assertThat(claims.provider()).isEqualTo(AuthProvider.FIREBASE_GOOGLE);
        assertThat(claims.subject()).describedAs("the Firebase uid, never the address").isEqualTo(UID);
        assertThat(claims.email()).isEqualTo(EMAIL);
        assertThat(claims.emailVerified()).isTrue();
    }

    @Test
    void rejectsATokenMintedForAnotherFirebaseProject() {
        // Every Firebase project on Earth is signed by the same Google key set, so the signature
        // alone proves only "some Firebase project issued this". Without the `aud` check, anyone
        // could create their own free project and mint a token for any address (ADR 009 §4).
        verifier.token = token("somebody-elses-project", ISSUER, "google.com", true);

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(InvalidProviderTokenException.class);
    }

    @Test
    void rejectsATokenWhoseIssuerIsNotOurProject() {
        verifier.token = token(PROJECT_ID, "https://securetoken.google.com/other", "google.com", true);

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(InvalidProviderTokenException.class);
    }

    @Test
    void rejectsAFirebaseEmailPasswordSignIn() {
        // The ADR 009 §4 assertion in one test. Enabling Email/Password in the Firebase console is
        // one toggle with no deploy and no review; without this check it would open an unvetted
        // registration path straight past /auth/register and its verification mail.
        verifier.token = token(PROJECT_ID, ISSUER, "password", true);

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(InvalidProviderTokenException.class);
    }

    @Test
    void rejectsATokenWithNoSignInProviderClaimAtAll() {
        verifier.token = token(PROJECT_ID, ISSUER, null, true);

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(InvalidProviderTokenException.class);
    }

    @Test
    void refusesAnUnverifiedGoogleAddressWithADistinguishableCode() {
        // PLAN §4.0.5's `firebase_email_not_verified`. Distinct from a bad token because the token
        // was fine — the user's next action is with Google, not with us.
        verifier.token = token(PROJECT_ID, ISSUER, "google.com", false);

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(ProviderEmailNotVerifiedException.class);
    }

    @Test
    void letsAnOutageThroughRatherThanReportingItAsABadToken() {
        // An outage downgraded to "no such identity" would let a retry create a second account.
        verifier.failure = new ProviderUnavailableException(new IllegalStateException("jwks down"));

        assertThatThrownBy(() -> adapter.authenticate(credential()))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private static ProviderCredential credential() {
        return new ProviderCredential(null, "an-id-token");
    }

    private static FirebaseIdToken token(String audience, String issuer, String signInProvider,
            boolean emailVerified) {
        return new FirebaseIdToken(audience, issuer, UID, EMAIL, emailVerified, signInProvider);
    }

    private static IdentityProviderProperties properties() {
        IdentityProviderProperties properties = new IdentityProviderProperties();
        properties.getFirebase().setProjectId(PROJECT_ID);
        return properties;
    }

    /** Stands in for the signature check, which is the one part that would need a network. */
    private static final class StubVerifier implements FirebaseTokenVerifier {

        private FirebaseIdToken token;
        private RuntimeException failure;

        @Override
        public FirebaseIdToken verify(String idToken) {
            if (failure != null) {
                throw failure;
            }
            return token;
        }
    }
}
