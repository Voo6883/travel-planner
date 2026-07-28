package com.travelplanner.infrastructure.auth.firebase;

/**
 * The claims of a Firebase ID token whose <em>signature</em> has been checked, before any policy has
 * been applied to them.
 *
 * <p>The split is deliberate. {@link FirebaseTokenVerifier} answers a cryptographic question — was
 * this signed by Google and is it still within its lifetime — and {@link FirebaseIdentityAdapter}
 * answers the policy questions ADR 009 §4 asks: is it for <em>this</em> project, and was the person
 * behind it authenticated by Google rather than by whatever else the Firebase console has switched
 * on. Keeping them apart is what lets the policy be tested exhaustively with no network and no
 * Firebase project.
 *
 * <p>Confined to {@code infrastructure/auth/firebase/}. Nothing in {@code application/} or
 * {@code domain/} names a Firebase concept — those see only {@code IdentityClaims}.
 *
 * @param audience the {@code aud} claim — the Firebase project the token was minted for
 * @param signInProvider {@code firebase.sign_in_provider}: which method actually authenticated the
 *        person. {@code google.com} for Google sign-in, {@code password} for Firebase's own
 *        Email/Password provider, and so on
 */
public record FirebaseIdToken(
        String audience,
        String issuer,
        String subject,
        String email,
        boolean emailVerified,
        String signInProvider) {
}
