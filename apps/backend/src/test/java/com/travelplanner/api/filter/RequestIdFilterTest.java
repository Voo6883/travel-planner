package com.travelplanner.api.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Request correlation (PLAN §4.0.2-J2). */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesTheCallerSuppliedIdAndExposesItToTheLogs() throws Exception {
        String supplied = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, supplied);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(seenInsideChain));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(supplied);
        assertThat(seenInsideChain.get()).isEqualTo(supplied);
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (rq, rs) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
    }

    @Test
    void rejectsAnIdThatCouldSplitAHeaderOrCorruptALogLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc\r\nX-Injected: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (rq, rs) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).doesNotContain("Injected");
    }

    @Test
    void clearsTheMdcSoAPooledThreadDoesNotMislabelTheNextRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (rq, rs) -> { });

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheChainThrows() {
        FilterChain failing = (rq, rs) -> {
            throw new IllegalStateException("downstream failure");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // The point of the test is the finally block, not the propagation.
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private static FilterChain capture(AtomicReference<String> sink) {
        return (rq, rs) -> sink.set(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
