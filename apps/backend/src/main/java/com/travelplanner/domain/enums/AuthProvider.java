package com.travelplanner.domain.enums;

/**
 * Sign-in providers (PLAN §4.0.5, §8). The constant name is the persisted
 * {@code user_identity.provider} value.
 *
 * <p>The adapters live in {@code infrastructure/auth/} and arrive with tasks 08 and 10. The enum
 * exists here because {@code user_identity} is created in this task and its CHECK constraint has
 * to agree with something the compiler can see.
 */
public enum AuthProvider {

    /** Email or username plus password, verified against a BCrypt hash held by this system. */
    LOCAL,

    /** Google, brokered through Firebase Authentication (ADR 004). */
    FIREBASE_GOOGLE,

    /** GitHub OAuth (ADR 004). */
    GITHUB;

    /** True when this system stores the credential, and so owns password and lockout rules. */
    public boolean isLocal() {
        return this == LOCAL;
    }
}
