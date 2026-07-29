package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

/** The party-size invariants task 18 has to enforce before a brief can be researched or priced. */
class PartySizeTest {

    @Test
    void countsAdultsAndChildrenSeparatelyAndTotalsThem() {
        PartySize family = new PartySize(2, 3);

        assertThat(family.adults()).isEqualTo(2);
        assertThat(family.children()).isEqualTo(3);
        assertThat(family.total()).isEqualTo(5);
    }

    @Test
    void aPartyWithNoAdultsIsRefused() {
        // A brief with zero adults would be researched and priced for nobody.
        assertThatThrownBy(() -> new PartySize(0, 2))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    void aNegativeChildCountIsRefused() {
        assertThatThrownBy(() -> new PartySize(2, -1))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aPartyLargerThanAnySupplierQuotesForIsRefused() {
        // Not a technical limit: above this, C4 stops quoting a single party, so the brief would
        // produce an itinerary that cannot be booked.
        assertThatThrownBy(() -> new PartySize(1, PartySize.MAX_TRAVELLERS))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(new PartySize(1, PartySize.MAX_TRAVELLERS - 1).total())
                .isEqualTo(PartySize.MAX_TRAVELLERS);
    }

    @Test
    void ofAdultsIsTheShapeASingleNumberClarificationAnswerProduces() {
        assertThat(PartySize.ofAdults(3)).isEqualTo(new PartySize(3, 0));
    }
}
