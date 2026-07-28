package com.travelplanner.application.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets, and the SHA-256 digests under which they are stored.
 *
 * <p><strong>Why SHA-256 and not BCrypt.</strong> BCrypt is deliberately slow to make a dictionary
 * attack expensive, which is the right trade for a human-chosen password. These values are 256 bits
 * from {@link SecureRandom}: there is no dictionary, nothing to slow down, and the lookup has to be
 * an indexed equality match rather than a scan-and-compare across every outstanding row. A digest
 * is both faster and — for full-entropy input — exactly as unguessable.
 *
 * <p><strong>Why store a digest at all.</strong> A verification or reset token is a bearer
 * credential for an account. Storing the raw value would make a database dump a list of working
 * ways into other people's accounts; storing the digest makes it a list of values that prove
 * nothing.
 *
 * <p>{@code RefreshTokenService} (task 08) carries an equivalent private helper. Folding the two
 * together would be a change to that task's file, so it is recorded as a follow-up rather than done
 * here — the shape and the constants are identical on purpose so that the merge is mechanical.
 */
public final class TokenDigest {

    /** 256 bits. Comfortably beyond guessing, and base64url-safe in a URL without escaping. */
    private static final int TOKEN_BYTES = 32;

    /** SHA-256 hex width — the {@code char(64)} that V4, V8, and V9 all declare. */
    public static final int HEX_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenDigest() {
    }

    /** @return a fresh secret. It exists here and in whatever is about to carry it, never in a log */
    public static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as lower-case hex, {@value #HEX_LENGTH} characters. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to ship SHA-256; if it is missing, the platform is broken.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
