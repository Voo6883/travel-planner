package com.travelplanner.api.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.trip.AnswerClarificationCommand;
import com.travelplanner.application.trip.SaveTripBriefCommand;
import com.travelplanner.application.trip.TripBriefService;
import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.DestinationNotCoveredException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.ClarificationAnswer;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The brief routing contract — the wire shape task 19's LLM tools and the intake form both have to
 * agree on.
 *
 * <p>See {@code TripControllerTest} for why filters are off and why a datasource URL is declared.
 */
@WebMvcTest(controllers = TripBriefController.class,
        properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/unused")
@AutoConfigureMockMvc(addFilters = false)
class TripBriefControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");
    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));
    private static final String BRIEF_PATH = "/api/v1/trips/" + TRIP_ID + "/brief";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripBriefService briefs;

    private static TripBriefDetails complete() {
        return TripBriefDetails.empty()
                .withDestinations(List.of("penang"))
                .withDates(SPRING)
                .withDateFlexibility(DateFlexibility.FLEXIBLE_WEEK)
                .withDepartureCity("Kuala Lumpur")
                .withBudget(Money.of("4000.00", "MYR"))
                .withParty(new PartySize(2, 1))
                .withInterests(List.of(TravelInterest.FOOD))
                .withPace(TravelPace.MODERATE);
    }

    private static TripBriefView viewOf(TripBriefDetails details, TripStatus status, int version) {
        TripBrief brief = new TripBrief(UUID.randomUUID(), TRIP_ID, details.destinations(),
                details.dates(), details.dateFlexibility(), details.departureCity(),
                details.budget(), details.party(), details.interests(), details.pace(), version,
                NOW, NOW);
        return new TripBriefView(brief, status, ClarificationNeeded.forDetails(details));
    }

    @Test
    void aCompleteBriefIsPublishedInSnakeCaseWithMoneyAsAnExactDecimalString() throws Exception {
        // A JSON number would reach a JavaScript client as a double and undo the BigDecimal /
        // numeric chain PLAN §4.0.2-A maintains everywhere else.
        when(briefs.get(any(UUID.class), any()))
                .thenReturn(viewOf(complete(), TripStatus.BRIEF_COMPLETE, 7));

        mockMvc.perform(get(BRIEF_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip_id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.status").value("BRIEF_COMPLETE"))
                .andExpect(jsonPath("$.destinations[0]").value("penang"))
                .andExpect(jsonPath("$.dates.start_date").value("2026-04-03"))
                .andExpect(jsonPath("$.date_flexibility").value("FLEXIBLE_WEEK"))
                .andExpect(jsonPath("$.departure_city").value("Kuala Lumpur"))
                .andExpect(jsonPath("$.budget.amount").value("4000.00"))
                .andExpect(jsonPath("$.budget.currency").value("MYR"))
                .andExpect(jsonPath("$.party.adults").value(2))
                .andExpect(jsonPath("$.party.children").value(1))
                .andExpect(jsonPath("$.interests[0]").value("FOOD"))
                .andExpect(jsonPath("$.pace").value("MODERATE"))
                .andExpect(jsonPath("$.clarification.questions").isEmpty())
                .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void anIncompleteBriefPublishesTypedQuestionsWithI18nPromptKeys() throws Exception {
        // "Never a silent guess" (PLAN §3.1), and never an English sentence the Malay build
        // could not reach.
        when(briefs.get(any(UUID.class), any())).thenReturn(
                viewOf(TripBriefDetails.empty(), TripStatus.CLARIFICATION_NEEDED, 1));

        mockMvc.perform(get(BRIEF_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLARIFICATION_NEEDED"))
                .andExpect(jsonPath("$.budget").doesNotExist())
                .andExpect(jsonPath("$.clarification.questions[0].id").value("travel_dates"))
                .andExpect(jsonPath("$.clarification.questions[0].prompt_key")
                        .value("trip_brief.clarify_dates"))
                .andExpect(jsonPath("$.clarification.questions[0].type").value("DATE_RANGE"))
                .andExpect(jsonPath("$.clarification.questions[0].required").value(true))
                .andExpect(jsonPath("$.clarification.questions[1].options")
                        .value(hasItem("FLEXIBLE_WEEK")));
    }

    @Test
    void theBriefNeverPublishesItsOwnSurrogateKey() throws Exception {
        // The brief is the trip's one child and is addressed only through tripId; a second
        // identifier would give clients two ways to name it and no endpoint that accepts one.
        when(briefs.get(any(UUID.class), any()))
                .thenReturn(viewOf(complete(), TripStatus.BRIEF_COMPLETE, 7));

        mockMvc.perform(get(BRIEF_PATH))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.brief_id").doesNotExist());
    }

    @Test
    void aSaveBindsEveryFieldIntoTheCommandAndReturnsTheIncrementedResource() throws Exception {
        when(briefs.save(any(SaveTripBriefCommand.class), any()))
                .thenReturn(viewOf(complete(), TripStatus.BRIEF_COMPLETE, 8));

        mockMvc.perform(put(BRIEF_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expected_version": 7,
                                  "destinations": ["penang"],
                                  "dates": {"start_date": "2026-04-03", "end_date": "2026-04-12"},
                                  "date_flexibility": "FLEXIBLE_WEEK",
                                  "departure_city": "Kuala Lumpur",
                                  "budget": {"amount": "4000.00", "currency": "MYR"},
                                  "party": {"adults": 2, "children": 1},
                                  "interests": ["FOOD"],
                                  "pace": "MODERATE"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(8))
                .andExpect(jsonPath("$.status").value("BRIEF_COMPLETE"));

        verify(briefs).save(new SaveTripBriefCommand(TRIP_ID, 7, complete()), null);
    }

    @Test
    void aSaveWithoutAnExpectedVersionIsValidationFailed() throws Exception {
        mockMvc.perform(put(BRIEF_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departure_city\":\"Penang\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.expected_version").exists());
    }

    @Test
    void aStaleSaveIsFourZeroNineCarryingTheCurrentVersion() throws Exception {
        when(briefs.save(any(SaveTripBriefCommand.class), any()))
                .thenThrow(new VersionConflictException(9));

        mockMvc.perform(put(BRIEF_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.details.current_version").value(9));
    }

    @Test
    void anUncoveredDestinationIsFourZeroFourCarryingTheSupportedList() throws Exception {
        // ADR 010 §4: the honest answer to "can you plan Osaka?" is "no, but here is what we do
        // know" — which needs the alternatives in the same response.
        when(briefs.save(any(SaveTripBriefCommand.class), any()))
                .thenThrow(new DestinationNotCoveredException("osaka", List.of("penang")));

        mockMvc.perform(put(BRIEF_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":7,\"destinations\":[\"osaka\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("destination_not_covered"))
                .andExpect(jsonPath("$.details.requested").value("osaka"))
                .andExpect(jsonPath("$.details.supported[0]").value("penang"));
    }

    @Test
    void aMalformedBudgetIsRejectedBeforeItReachesTheService() throws Exception {
        mockMvc.perform(put(BRIEF_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":7,"
                                + "\"budget\":{\"amount\":\"-5\",\"currency\":\"MYR\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void answeringClarificationIsATypedPostActionRatherThanAPut() throws Exception {
        // ADR 008 §3 supersedes PLAN §4.1.3's PUT .../brief/clarification: re-sending nine fields
        // to answer one question is nine chances to clobber what the agent just wrote.
        when(briefs.answerClarification(any(AnswerClarificationCommand.class), any()))
                .thenReturn(viewOf(complete(), TripStatus.BRIEF_COMPLETE, 8));

        mockMvc.perform(post(BRIEF_PATH + "/actions/answer-clarification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expected_version": 7,
                                  "answers": [
                                    {"question_id": "budget_max",
                                     "money": {"amount": "4000.00", "currency": "MYR"}},
                                    {"question_id": "party_size", "number": 2}
                                  ]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(8));

        verify(briefs).answerClarification(new AnswerClarificationCommand(TRIP_ID, 7, List.of(
                ClarificationAnswer.ofMoney("budget_max", Money.of("4000.00", "MYR")),
                ClarificationAnswer.ofNumber("party_size", 2))), null);
    }

    @Test
    void anActionThatAnswersNothingIsRejected() throws Exception {
        // It would consume a version and change no state, leaving the client unable to tell which
        // of the two happened.
        mockMvc.perform(post(BRIEF_PATH + "/actions/answer-clarification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":7,\"answers\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void anAnswerCarryingTwoValuesIsRejected() throws Exception {
        mockMvc.perform(post(BRIEF_PATH + "/actions/answer-clarification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":7,\"answers\":["
                                + "{\"question_id\":\"pace\",\"choice\":\"RELAXED\",\"number\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }
}
