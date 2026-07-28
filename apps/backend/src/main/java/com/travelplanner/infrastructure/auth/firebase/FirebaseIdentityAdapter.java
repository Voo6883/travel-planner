package com.travelplanner.infrastructure.auth.firebase;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.exception.InvalidProviderTokenException;
import com.travelplanner.domain.exception.ProviderEmailNotVerifiedException;
import com.travelplanner.domain.port.IdentityProviderPort;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@code FIREBASE_GOOGLE} identity adapter (PLAN §4.0.5).
 *
 * <p>Declaring this bean is the whole of "add a provider": {@link com.travelplanner.application.auth
 * .IdentityProviderRegistry} routes on {@link #provider()}, so no service acquired a second code
 * path and nothing in {@code application/} names Firebase. That was task 08's stated purpose for
 * the registry, and this is the first use of it.
 *
 * <h2>Three assertions, and why each one is load-bearing</h2>
 *
 * <ol>
 *   <li><b>Signature and lifetime</b> — {@link FirebaseTokenVerifier}. ADR 004 Security: verify the
 *       ID token server-side, never trust client claims.
 *   <li><b>{@code aud} equals our project id</b> (ADR 009 §4). Firebase ID tokens are signed by one
 *       Google key set shared by <em>every</em> Firebase project on Earth, so the signature alone
 *       proves only "some Firebase project issued this". Without the audience check, anyone could
 *       create their own free Firebase project, mint a token for any address, and sign in here.
 *   <li><b>{@code firebase.sign_in_provider} is {@code google.com}</b> (ADR 009 §4). Firebase
 *       issues the same token shape for every method the console has enabled. Switching on
 *       Email/Password — one toggle, no deploy, no review — would otherwise let anyone self-issue an
 *       identity for any address they typed, an unvetted registration path straight past
 *       {@code /auth/register} and its verification mail.
 * </ol>
 *
 * <p>{@code iss} is checked alongside {@code aud} because Google specifies both, and a token whose
 * issuer is not {@code https://securetoken.google.com/<project>} is not a Firebase ID token at all.
 *
 * <p>All three failures raise one {@link InvalidProviderTokenException}. A distinct code per cause
 * would tell somebody probing the endpoint exactly which console setting to attack next.
 */
@Component
public class FirebaseIdentityAdapter implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(FirebaseIdentityAdapter.class);

    private final FirebaseTokenVerifier verifier;
    private final IdentityProviderProperties.Firebase properties;

    public FirebaseIdentityAdapter(FirebaseTokenVerifier verifier,
            IdentityProviderProperties properties) {
        this.verifier = verifier;
        this.properties = properties.getFirebase();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.FIREBASE_GOOGLE;
    }

    @Override
    public IdentityClaims authenticate(ProviderCredential credential) {
        FirebaseIdToken token = verifier.verify(credential.secret());
        requireOurProject(token);
        requireGoogleSignIn(token);
        if (!token.emailVerified()) {
            // Distinguishable from a bad token: the token was fine, and the user's correct next
            // action is to confirm their address with Google, not to sign in again.
            throw new ProviderEmailNotVerifiedException();
        }
        return new IdentityClaims(AuthProvider.FIREBASE_GOOGLE, token.subject(), token.email(), true);
    }

    /**
     * ADR 009 §4. The audience is the only thing separating our project's tokens from those of any
     * other Firebase project, all of which carry a valid Google signature.
     */
    private void requireOurProject(FirebaseIdToken token) {
        if (!properties.getProjectId().equals(token.audience())
                || !properties.issuer().equals(token.issuer())) {
            log.warn("firebase_token_rejected — audience or issuer does not match this project");
            throw new InvalidProviderTokenException();
        }
    }

    /** ADR 009 §4. Anything but Google means a Firebase console setting opened another door. */
    private void requireGoogleSignIn(FirebaseIdToken token) {
        if (!properties.getRequiredSignInProvider().equals(token.signInProvider())) {
            log.warn("firebase_token_rejected — sign_in_provider '{}' is not permitted",
                    token.signInProvider());
            throw new InvalidProviderTokenException();
        }
    }
}
