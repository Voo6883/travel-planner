package com.travelplanner.api.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's address, which is half of the ADR 009 §6 lockout key.
 *
 * <p><b>The trust model, stated plainly.</b> ADR 006 puts a Next.js rewrite proxy in front of this
 * service, so {@code getRemoteAddr()} is the proxy for every browser request and
 * {@code X-Forwarded-For} carries the real client. That header is client-supplied and therefore
 * forgeable by anyone who can reach the backend directly.
 *
 * <p>Forging it does not break the lockout, it only dilutes it. The key is
 * {@code (identifier, address)}: an attacker who rotates the header gets a fresh five attempts per
 * fabricated address, which is the same position they would be in with a botnet — and which is
 * what the per-endpoint rate limiting PLAN §4.0.9 schedules for Phase 1+ exists to address. What
 * the header cannot do is unlock somebody else or lock a victim out of their own machine, because
 * a forged address is not the victim's address.
 *
 * <p>Only the first entry is used. The header is a comma-separated chain and every hop after the
 * first was appended by an intermediary, not by the client.
 */
public final class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    /** Matches {@code login_attempt.client_ip varchar(45)} — the longest IPv6 text form. */
    private static final int MAX_LENGTH = 45;

    private static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = firstHopOf(request.getHeader(FORWARDED_FOR));
        String resolved = forwarded != null ? forwarded : request.getRemoteAddr();
        return sanitise(resolved);
    }

    private static String firstHopOf(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String first = (comma < 0 ? header : header.substring(0, comma)).trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * The value reaches a database column and a log line, so it is bounded and stripped of
     * anything that could corrupt either. An over-long or unparseable value degrades to a shared
     * bucket rather than being trusted verbatim.
     */
    private static String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return UNKNOWN;
        }
        return candidate.chars().allMatch(ClientIpResolver::isAddressCharacter)
                ? candidate
                : UNKNOWN;
    }

    private static boolean isAddressCharacter(int character) {
        return Character.isLetterOrDigit(character) || character == '.' || character == ':'
                || character == '%';
    }
}
