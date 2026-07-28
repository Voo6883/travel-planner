package com.travelplanner.infrastructure.auth.firebase;

/**
 * Checks that an ID token was really signed by Google and has not expired, and reports its claims.
 *
 * <p>A seam with two implementations, chosen by {@code IdentityProviderConfig}: the real one
 * verifies against Google's published keys, and the development one does not verify at all and
 * refuses to exist in production. Task 10 forbids relying on a live provider in CI, and this is
 * where that is arranged — the policy in {@link FirebaseIdentityAdapter} runs identically either
 * way, so the rules ADR 009 §4 cares about are exercised in every environment.
 *
 * <p>Signature checking only. Whether the token is for <em>this</em> project, and whether Google
 * (rather than some other Firebase provider) authenticated the person, are decisions with security
 * consequences that belong beside the account rules rather than beside the crypto.
 */
public interface FirebaseTokenVerifier {

    /**
     * @throws com.travelplanner.domain.exception.InvalidProviderTokenException when the token is
     *         malformed, unsigned by a known key, or expired
     * @throws com.travelplanner.domain.exception.ProviderUnavailableException when Google's key set
     *         cannot be fetched — an outage must never be reported as a bad token, because that
     *         would turn a network blip into a second account for the same person
     */
    FirebaseIdToken verify(String idToken);
}
