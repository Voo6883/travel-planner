package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;

/**
 * Verifies a credential with one sign-in provider and reports what it asserts (PLAN §4.0.5).
 *
 * <p>This is the seam that keeps vendor SDKs out of {@code application/} and out of controllers.
 * Adapters live in {@code infrastructure/auth/}: {@code local/LocalPasswordIdentityAdapter} here,
 * {@code firebase/} and {@code github/} in task 10. The Firebase Admin SDK and the GitHub client
 * are imported by those packages and nowhere else.
 *
 * <p>An implementation <em>verifies</em>. It never creates accounts, never issues sessions, and
 * never decides whether a verified identity may be linked to an existing user — that policy is
 * ADR 009 §4 and belongs to the application layer.
 *
 * <p>Failure is signalled by {@code InvalidCredentialsException}, uniformly, for every reason.
 * Distinguishing "no such account" from "wrong password" at this level would make the port itself
 * an account-existence oracle no caller could close.
 */
public interface IdentityProviderPort {

    /** Which provider this adapter speaks for. Used to route a request to exactly one adapter. */
    AuthProvider provider();

    /**
     * @throws com.travelplanner.domain.exception.InvalidCredentialsException if the credential does
     *         not verify, for any reason
     */
    IdentityClaims authenticate(ProviderCredential credential);
}
