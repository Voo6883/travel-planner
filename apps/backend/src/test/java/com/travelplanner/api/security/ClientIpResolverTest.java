package com.travelplanner.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Half of the ADR 009 §6 lockout key, behind a reverse proxy (ADR 006). */
class ClientIpResolverTest {

    @Test
    void usesTheSocketAddressWhenThereIsNoProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void prefersTheFirstForwardedHopBecauseThatIsTheClient() {
        // ADR 006 puts a Next.js rewrite proxy in front, so getRemoteAddr() is the proxy for every
        // browser request. Later entries in the chain were appended by intermediaries.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader(ClientIpResolver.FORWARDED_FOR, "203.0.113.7, 10.0.0.2, 10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void keepsIpv6Addresses() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void degradesToASharedBucketRatherThanTrustingAHostileHeader() {
        // The value reaches a varchar(45) column and a log line. An over-long or control-character
        // payload must not corrupt either, and must not be stored verbatim.
        MockHttpServletRequest injected = new MockHttpServletRequest();
        injected.setRemoteAddr("10.0.0.1");
        injected.addHeader(ClientIpResolver.FORWARDED_FOR, "evil\nX-Admin: true");

        MockHttpServletRequest oversized = new MockHttpServletRequest();
        oversized.setRemoteAddr("10.0.0.1");
        oversized.addHeader(ClientIpResolver.FORWARDED_FOR, "9".repeat(200));

        assertThat(ClientIpResolver.resolve(injected)).isEqualTo("unknown");
        assertThat(ClientIpResolver.resolve(oversized)).isEqualTo("unknown");
    }

    @Test
    void ignoresAnEmptyForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader(ClientIpResolver.FORWARDED_FOR, "   ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }
}
