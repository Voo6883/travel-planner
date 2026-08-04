package com.travelplanner.application.tripchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

class ResearchChatToolArgsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void startResearchRequiresUserConfirmedTrue() {
        assertThatThrownBy(() -> StartResearchArgs.parse(
                TripChatTools.START_RESEARCH, "{\"user_confirmed\":false}", mapper))
                .isInstanceOf(ValidationFailedException.class);
        StartResearchArgs ok = StartResearchArgs.parse(
                TripChatTools.START_RESEARCH, "{\"user_confirmed\":true}", mapper);
        assertThat(ok.userConfirmed()).isTrue();
    }

    @Test
    void selectRecommendationRequiresIdForExplicitAndAllowsJustPickWithout() {
        assertThatThrownBy(() -> SelectRecommendationArgs.parse(
                TripChatTools.SELECT_RECOMMENDATION, "{\"confirmation\":\"explicit\"}", mapper))
                .isInstanceOf(ValidationFailedException.class);

        SelectRecommendationArgs justPick = SelectRecommendationArgs.parse(
                TripChatTools.SELECT_RECOMMENDATION, "{\"confirmation\":\"just_pick\"}", mapper);
        assertThat(justPick.confirmation()).isEqualTo(SelectRecommendationArgs.Confirmation.JUST_PICK);
        assertThat(justPick.recommendationId()).isNull();
    }

    @Test
    void unknownFieldsAreRejected() {
        assertThatThrownBy(() -> EmptyToolArgs.parse(
                TripChatTools.GET_RESEARCH_STATUS, "{\"extra\":1}", mapper))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void toolsAreStatusGated() {
        assertThat(TripChatTools.specsFor(com.travelplanner.domain.enums.TripStatus.BRIEF_COMPLETE))
                .extracting(spec -> spec.name())
                .containsExactly(TripChatTools.START_RESEARCH);
        assertThat(TripChatTools.isAllowed(
                com.travelplanner.domain.enums.TripStatus.DRAFT, TripChatTools.START_RESEARCH))
                .isFalse();
        assertThat(TripChatTools.isAllowed(
                com.travelplanner.domain.enums.TripStatus.RESEARCH_READY,
                TripChatTools.SELECT_RECOMMENDATION))
                .isTrue();
        assertThat(TripChatTools.isAllowed(
                com.travelplanner.domain.enums.TripStatus.RESEARCH_QUEUED,
                TripChatTools.SELECT_RECOMMENDATION))
                .isFalse();
    }
}
