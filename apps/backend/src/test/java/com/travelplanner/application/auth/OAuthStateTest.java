package com.travelplanner.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The OAuth {@code state} nonce (ADR 004 Security).
 *
 * <p>Small, and worth pinning: {@code state} is the only thing standing between the GitHub callback
 * and login CSRF, and every failure mode here is silent. A parser that accepted a truncated cookie,
 * or a comparison that returned {@code true} for a blank nonce, would leave the endpoint open with
 * no symptom.
 */
class OAuthStateTest {

    @Test
    void issuesAnUnguessableNonceThatDiffersEveryTime() {
        OAuthState first = OAuthState.issue(false);
        OAuthState second = OAuthState.issue(false);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        // 256 bits in base64url, which is 43 characters unpadded.
        assertThat(first.nonce()).hasSize(43);
    }

    @Test
    void roundTripsTheModeThroughTheCookieValue() {
        // The mode travels in the httpOnly cookie rather than through the provider, so an attacker
        // cannot flip a sign-in into a link.
        OAuthState link = OAuthState.issue(true);

        OAuthState parsed = OAuthState.parse(link.cookieValue()).orElseThrow();

        assertThat(parsed.linkMode()).isTrue();
        assertThat(parsed.nonce()).isEqualTo(link.nonce());
        assertThat(OAuthState.parse(OAuthState.issue(false).cookieValue()).orElseThrow().linkMode())
                .isFalse();
    }

    @Test
    void refusesAnythingItDidNotWrite() {
        assertThat(OAuthState.parse(null)).isEmpty();
        assertThat(OAuthState.parse("")).isEmpty();
        assertThat(OAuthState.parse("no-separator")).isEmpty();
        assertThat(OAuthState.parse(":nonce-with-no-mode")).isEmpty();
        assertThat(OAuthState.parse("S:")).describedAs("truncated").isEmpty();
        assertThat(OAuthState.parse("X:nonce")).describedAs("unknown mode").isEmpty();
    }

    @Test
    void matchesOnlyTheExactNonce() {
        OAuthState state = OAuthState.issue(false);

        assertThat(state.matches(state.nonce())).isTrue();
        assertThat(state.matches(null)).isFalse();
        assertThat(state.matches("")).isFalse();
        assertThat(state.matches(state.nonce() + "x")).isFalse();
        assertThat(state.matches(state.nonce().substring(1))).isFalse();
    }

    @Test
    void keepsANonceContainingTheSeparatorIntact() {
        // Base64url never emits ':', but splitting on the last separator instead of the first would
        // silently corrupt any future encoding that did.
        Optional<OAuthState> parsed = OAuthState.parse("L:a:b");

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().nonce()).isEqualTo("a:b");
    }
}
