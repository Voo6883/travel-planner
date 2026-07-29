package com.travelplanner.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class DestinationNotCoveredExceptionTest {

    @Test
    void saysWhatWasAskedForAndWhatCouldBeAskedForInstead() {
        DestinationNotCoveredException refusal =
                new DestinationNotCoveredException("osaka-jp", List.of("tokyo-jp", "kyoto-jp"));

        assertThat(refusal.code()).isEqualTo("destination_not_covered");
        assertThat(refusal.getMessage()).contains("osaka-jp");
        assertThat(refusal.supportedSlugs()).containsExactly("tokyo-jp", "kyoto-jp");
        assertThat(refusal.details())
                .containsEntry("requested", "osaka-jp")
                .containsEntry("supported", List.of("tokyo-jp", "kyoto-jp"));
    }

    @Test
    void isADomainExceptionSoTheHttpAdapterOwnsTheStatusCode() {
        assertThat(new DestinationNotCoveredException("osaka-jp", List.of()))
                .isInstanceOf(DomainException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void copiesTheSupportedListSoALaterMutationCannotRewriteTheRefusal() {
        List<String> supported = new ArrayList<>(List.of("tokyo-jp"));
        DestinationNotCoveredException refusal =
                new DestinationNotCoveredException("osaka-jp", supported);

        supported.add("osaka-jp");

        assertThat(refusal.supportedSlugs()).containsExactly("tokyo-jp");
        assertThat(refusal.details()).containsEntry("supported", List.of("tokyo-jp"));
    }

    @Test
    void exposesTheSupportedListAsImmutable() {
        // The exception is handed straight to the error handler and serialised; a caller that could
        // append to this list would be editing the response body after the fact.
        DestinationNotCoveredException refusal =
                new DestinationNotCoveredException("osaka-jp", List.of("tokyo-jp"));

        assertThatThrownBy(() -> refusal.supportedSlugs().add("osaka-jp"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> refusal.details().put("requested", "kyoto-jp"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesToBeBuiltWithoutARequestedSlugOrASupportedList() {
        assertThatThrownBy(() -> new DestinationNotCoveredException(null, List.of("tokyo-jp")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationNotCoveredException("osaka-jp", null))
                .isInstanceOf(NullPointerException.class);
    }
}
