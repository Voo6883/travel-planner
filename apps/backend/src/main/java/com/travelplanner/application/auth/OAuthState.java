package com.travelplanner.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * The single-use {@code state} for one OAuth round trip, plus what the round trip is for
 * (ADR 004 Security: "`state` param CSRF protection on OAuth; validate callback").
 *
 * <h2>Why a value and not a database row</h2>
 *
 * <p>The nonce goes to the browser twice — in the authorize URL and in an {@code httpOnly} cookie —
 * and the callback accepts nothing unless the two match. Only a browser that started the flow holds
 * the cookie, so a callback forged by another site has nothing to present. That is the whole
 * mechanism, and it needs no server-side storage, which keeps the locked stateless posture
 * (ADR 002) intact.
 *
 * <h2>Why the mode travels in the cookie and not in the URL</h2>
 *
 * <p>{@code linkMode} decides whether the callback attaches an identity to the signed-in account or
 * signs somebody in. Putting it in the authorize URL would send it through GitHub and back as
 * caller-controlled input — an attacker could flip a sign-in into a link. In the {@code httpOnly}
 * cookie it is server-issued and unreachable from script, so only the value we wrote comes back.
 *
 * <p>Framework-free and side-effect-free apart from {@link SecureRandom}, so the whole rule is unit
 * testable without an HTTP request.
 */
public record OAuthState(boolean linkMode, String nonce) {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 256 bits, the same strength as every other unguessable value in this system. */
    private static final int NONCE_BYTES = 32;

    private static final char SEPARATOR = ':';
    private static final String LINK = "L";
    private static final String SIGN_IN = "S";

    public OAuthState {
        Objects.requireNonNull(nonce, "nonce");
        if (nonce.isBlank()) {
            throw new IllegalArgumentException("nonce must not be blank");
        }
    }

    public static OAuthState issue(boolean linkMode) {
        byte[] entropy = new byte[NONCE_BYTES];
        RANDOM.nextBytes(entropy);
        return new OAuthState(linkMode, Base64.getUrlEncoder().withoutPadding().encodeToString(entropy));
    }

    /** What goes in the {@code httpOnly} cookie: the mode and the nonce together. */
    public String cookieValue() {
        return (linkMode ? LINK : SIGN_IN) + SEPARATOR + nonce;
    }

    /** Empty for anything this class did not write — a truncated, forged, or stale cookie. */
    public static Optional<OAuthState> parse(String cookieValue) {
        if (cookieValue == null) {
            return Optional.empty();
        }
        int separator = cookieValue.indexOf(SEPARATOR);
        if (separator <= 0 || separator == cookieValue.length() - 1) {
            return Optional.empty();
        }
        String mode = cookieValue.substring(0, separator);
        if (!LINK.equals(mode) && !SIGN_IN.equals(mode)) {
            return Optional.empty();
        }
        return Optional.of(new OAuthState(LINK.equals(mode), cookieValue.substring(separator + 1)));
    }

    /**
     * Constant-time comparison. {@link String#equals} returns at the first differing character, and
     * the timing of that is a side channel for guessing a nonce one character at a time — cheap to
     * avoid, and the sort of thing nobody adds later.
     */
    public boolean matches(String presentedNonce) {
        if (presentedNonce == null) {
            return false;
        }
        return MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                presentedNonce.getBytes(StandardCharsets.UTF_8));
    }
}
