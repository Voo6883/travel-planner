package com.travelplanner.ai.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.prompt.PromptTemplateStore;
import com.travelplanner.ai.stub.StubLlmAdapter;
import com.travelplanner.ai.structured.StructuredOutputRunner;
import com.travelplanner.domain.ai.MessageRole;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripBriefExtractionOutcome;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;
import com.travelplanner.domain.port.LlmPort;
import com.travelplanner.domain.port.TripBriefExtractionPort;
import com.travelplanner.domain.valueobject.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The adapter's own contract: how the prompt is assembled, what happens when the provider fails,
 * and that the default stub deployment degrades to the form rather than to a broken screen.
 *
 * <p>Case-by-case extraction behaviour lives in {@link TripBriefGoldenFileTest}, which is the
 * evaluation harness; this class covers what a fixture cannot express.
 */
class LlmTripBriefExtractorTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);

    private static final String COMPLETE_REPLY = """
            {"destinations":["penang"],"surprise_me":false,"start_date":"2026-04-03",
             "end_date":"2026-04-12","date_flexibility":"FIXED","departure_city":"Kuala Lumpur",
             "budget_amount":"4000.00","budget_currency":"MYR","adults":2,"children":0,
             "interests":["FOOD"],"pace":"RELAXED","ambiguous_fields":[]}""";

    private final ScriptedLlm llm = new ScriptedLlm();

    @Test
    void theSystemBlockComesFirstAndTheTravellersWordsComeLastAndFenced() {
        llm.reply(COMPLETE_REPLY);

        extractor(llm).extract(request("We want Penang in April."));

        Prompt sent = llm.prompts().get(0);
        assertThat(sent.messages().get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(sent.conversation()).hasSize(1);
        assertThat(sent.conversation().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(sent.conversation().get(0).text())
                .isEqualTo("<USER_TEXT>\nWe want Penang in April.\n</USER_TEXT>");
    }

    @Test
    void thePromptCarriesTheTemplateIdentitySoTheCallLogCanNameTheVersion() {
        llm.reply(COMPLETE_REPLY);

        extractor(llm).extract(request("We want Penang in April."));

        Prompt sent = llm.prompts().get(0);
        assertThat(sent.templateId()).isEqualTo(TripBriefExtractionPrompt.ID);
        assertThat(sent.templateVersion()).isEqualTo(TripBriefExtractionPrompt.VERSION);
    }

    @Test
    void extractionIsMergedOntoWhatTheBriefAlreadyHeldRatherThanReplacingIt() {
        // A follow-up message about the pace must not erase the budget the form already saved.
        llm.reply("""
                {"surprise_me":false,"pace":"PACKED","ambiguous_fields":[]}""");
        TripBriefDetails known = TripBriefDetails.empty().withBudget(Money.of("900.00", "MYR"));

        TripBriefExtraction extraction = extractor(llm)
                .extract(new TripBriefExtractionRequest("Actually, pack it full.", "en", known));

        assertThat(extraction.details().pace()).isEqualTo(TravelPace.PACKED);
        assertThat(extraction.details().budget()).isEqualTo(Money.of("900.00", "MYR"));
    }

    @Test
    void aRetryableProviderFailureFallsBackToTheFormInsteadOfThrowing() {
        llm.failsWith(AiProviderException.unavailable("connection refused"));
        TripBriefDetails known = TripBriefDetails.empty().withDepartureCity("Ipoh");

        TripBriefExtraction extraction = extractor(llm)
                .extract(new TripBriefExtractionRequest("Penang in April.", "en", known));

        assertThat(extraction.outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(extraction.failureCode()).isEqualTo(AiProviderException.UNAVAILABLE);
        assertThat(extraction.details()).isEqualTo(known);
        assertThat(extraction.clarification().questions())
                .extracting(ClarificationQuestion::id)
                .doesNotContain(ClarificationNeeded.QUESTION_DEPARTURE_CITY);
    }

    /**
     * The default provider on a fresh checkout, in CI, and in any deployment with no API key. It
     * answers with a visibly-stub sentence rather than JSON, so extraction degrades to the intake
     * form — which is the correct behaviour and is asserted rather than assumed.
     */
    @Test
    void theDeterministicStubProviderDegradesToTheFormWithNoApiKey() {
        TripBriefExtraction extraction = extractor(new StubLlmAdapter())
                .extract(request("Penang in April for two, RM4000."));

        assertThat(extraction.outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(extraction.failureCode()).isEqualTo(AiProviderException.RESPONSE_INVALID);
        assertThat(extraction.details()).isEqualTo(TripBriefDetails.empty());
        assertThat(extraction.clarification().questions()).hasSize(7);
    }

    @Test
    void anUnsupportedLocaleIsReadAsEnglishRatherThanRefused() {
        llm.reply(COMPLETE_REPLY);

        extractor(llm).extract(new TripBriefExtractionRequest("Penang in April.", "de-DE",
                TripBriefDetails.empty()));

        assertThat(llm.prompts().get(0).systemText()).contains("language \"en\"");
    }

    @Test
    void theClockDecidesWhatTodayMeansSoARelativeDateIsReproducible() {
        llm.reply(COMPLETE_REPLY);

        extractor(llm).extract(request("Next April, please."));

        assertThat(llm.prompts().get(0).systemText()).contains("Today is 2026-01-15.");
    }

    private static TripBriefExtractionRequest request(String userText) {
        return TripBriefExtractionRequest.of(userText, "en");
    }

    private static TripBriefExtractionPort extractor(LlmPort provider) {
        PromptTemplateStore store = new PromptTemplateStore();
        TripBriefExtractionPrompt.register(store);
        return new LlmTripBriefExtractor(
                new StructuredOutputRunner(provider, new ObjectMapper()), store, CLOCK);
    }
}
