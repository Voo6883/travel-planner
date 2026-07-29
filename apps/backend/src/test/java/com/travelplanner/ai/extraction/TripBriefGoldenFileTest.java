package com.travelplanner.ai.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.prompt.PromptTemplateStore;
import com.travelplanner.ai.structured.StructuredOutputRunner;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripBriefExtractionOutcome;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The evaluation harness for {@code trip-brief-extract} (task 19 Validation; AI-AGENT-WORKFLOW A3,
 * A7).
 *
 * <p>Each fixture in {@code src/test/resources/ai/trip-brief/} pairs a traveller's message with the
 * reply a model gave — or failed to give — for it. The test then runs the <em>real</em> prompt, the
 * real {@code StructuredOutputRunner}, and the real domain validation, replacing only the network.
 * A prompt change that alters extraction behaviour therefore fails here rather than in production,
 * which is the evaluation gate the Definition of Done asks for. It needs no API key and no Docker.
 *
 * <h2>Thresholds</h2>
 *
 * <p><strong>Schema validity — 100% first-attempt binding.</strong> Every fixture whose reply is
 * well-formed JSON must bind to {@link TripBriefPayload} on attempt one, with zero repairs. This is
 * not an aspiration about live models; it is a statement about this code: given conforming output,
 * the schema, the payload, and the extractor agree. A repair consumed here would mean the three had
 * drifted apart. Live-model conformance is a separate, manually approved run (task 19 Validation)
 * and does not gate CI.
 *
 * <p><strong>Repair budget — at most one, then the form.</strong> Non-conforming output gets
 * exactly one further attempt with the parse error fed back, and then the extraction falls back.
 * {@code malformed} asserts the count, so an added retry loop fails the build.
 *
 * <p><strong>Clarification — 0% acceptance of anything unconfirmed.</strong> A value the model
 * flagged as uncertain, and a value a domain invariant refuses, are accepted at a rate of zero and
 * become questions instead. {@code vague}, {@code invalid-dates}, {@code impossible-budget}, and
 * {@code injection} are the four shapes that can happen, and each asserts both halves: the field is
 * absent from the brief <em>and</em> present in the question list. There is no confidence score and
 * no threshold to tune — a number here would be a dial somebody eventually turns down.
 *
 * <p><strong>Fallback — every provider failure, no exceptions.</strong> {@code timeout} and
 * {@code malformed} assert {@link TripBriefExtractionOutcome#FALLBACK} with the error code
 * attached and the brief untouched.
 */
class TripBriefGoldenFileTest {

    /** Fixed, so "3 to 12 April" resolves the same way on every machine and every day. */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);

    // -------------------------------------------------------------------------------------
    // Complete, incomplete, vague
    // -------------------------------------------------------------------------------------

    @Test
    void aCompleteMessageYieldsABriefThatWouldUnblockResearch() {
        Run run = run("complete.json");

        assertThat(run.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.EXTRACTED);
        assertThat(run.extraction().isComplete()).isTrue();
        assertThat(run.extraction().unresolvedFields()).isEmpty();
        TripBriefDetails details = run.extraction().details();
        assertThat(details.destinations()).containsExactly("penang");
        assertThat(details.dates().start()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(details.dates().end()).isEqualTo(LocalDate.of(2026, 4, 12));
        assertThat(details.dateFlexibility()).isEqualTo(DateFlexibility.FIXED);
        assertThat(details.departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(details.budget()).isEqualTo(Money.of("4000.00", "MYR"));
        assertThat(details.party()).isEqualTo(new PartySize(2, 0));
        assertThat(details.interests()).containsExactly(TravelInterest.FOOD);
        assertThat(details.pace()).isEqualTo(TravelPace.RELAXED);
    }

    @Test
    void anIncompleteMessageAsksForExactlyWhatIsMissingAndInventsNothing() {
        Run run = run("incomplete.json");

        assertThat(run.extraction().isComplete()).isFalse();
        assertThat(questionIds(run.extraction())).containsExactly(
                ClarificationNeeded.QUESTION_DATE_FLEXIBILITY,
                ClarificationNeeded.QUESTION_DEPARTURE_CITY,
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_INTERESTS,
                ClarificationNeeded.QUESTION_PACE);
        assertThat(run.extraction().details().budget()).isNull();
        assertThat(run.extraction().details().departureCity()).isNull();
        assertThat(run.extraction().details().party()).isEqualTo(new PartySize(2, 0));
    }

    @Test
    void aValueTheModelIsUnsureOfIsAskedAboutRatherThanUsed() {
        // The whole point of ambiguous_fields: the dates parse perfectly and are still discarded,
        // because "sometime in spring" is not an answer the traveller gave.
        Run run = run("vague.json");

        assertThat(run.extraction().unresolvedFields()).containsExactlyInAnyOrder(
                ClarificationNeeded.QUESTION_TRAVEL_DATES,
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_PARTY_SIZE);
        assertThat(run.extraction().details().dates()).isNull();
        assertThat(run.extraction().details().budget()).isNull();
        assertThat(run.extraction().details().party()).isNull();
        assertThat(questionIds(run.extraction())).contains(
                ClarificationNeeded.QUESTION_TRAVEL_DATES,
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_PARTY_SIZE);
        // What it was confident about survives — refusal is per field, not per message.
        assertThat(run.extraction().details().dateFlexibility())
                .isEqualTo(DateFlexibility.FLEXIBLE_MONTH);
    }

    // -------------------------------------------------------------------------------------
    // Domain validation is authoritative
    // -------------------------------------------------------------------------------------

    @Test
    void aReversedDateRangeIsRefusedByDateRangeItself() {
        Run run = run("invalid-dates.json");

        assertThat(run.extraction().unresolvedFields())
                .containsExactly(ClarificationNeeded.QUESTION_TRAVEL_DATES);
        assertThat(run.extraction().details().dates()).isNull();
        assertThat(questionIds(run.extraction()))
                .contains(ClarificationNeeded.QUESTION_TRAVEL_DATES);
        assertThat(run.extraction().details().departureCity()).isEqualTo("Ipoh");
    }

    @Test
    void aNegativeBudgetAndAThirtyPersonPartyAreRefusedByMoneyAndPartySize() {
        // The rules a form submission would hit, hit here too — same value objects, no second copy.
        Run run = run("impossible-budget.json");

        assertThat(run.extraction().unresolvedFields()).containsExactlyInAnyOrder(
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_PARTY_SIZE);
        assertThat(run.extraction().details().budget()).isNull();
        assertThat(run.extraction().details().party()).isNull();
        assertThat(run.extraction().isComplete()).isFalse();
    }

    // -------------------------------------------------------------------------------------
    // Multilingual, surprise me, uncovered destinations
    // -------------------------------------------------------------------------------------

    @Test
    void malayInputExtractsTheSameBriefAndTheLocaleReachesThePrompt() {
        Run run = run("multilingual-ms.json");

        assertThat(run.extraction().isComplete()).isTrue();
        assertThat(run.extraction().details().budget()).isEqualTo(Money.of("4000.00", "MYR"));
        assertThat(run.llm().prompts().get(0).systemText()).contains("language \"ms\"");
    }

    @Test
    void surpriseMeClearsTheDestinationTheModelNamedAnyway() {
        // UC-C1-05: an open destination is destinations=[] plus the flag, and the traveller's
        // "surprise me" outranks a model that could not resist suggesting Bali.
        Run run = run("surprise-me.json");

        assertThat(run.extraction().surpriseMe()).isTrue();
        assertThat(run.extraction().details().destinations()).isEmpty();
        assertThat(run.extraction().isComplete()).isTrue();
    }

    @Test
    void anUncoveredDestinationSurvivesExtractionForTheCallerToRefuse() {
        // The port does not know what is covered — KnowledgePort is only visible to the
        // application layer. TripBriefExtractionServiceTest asserts the refusal itself.
        Run run = run("unsupported-destination.json");

        assertThat(run.extraction().details().destinations()).containsExactly("osaka");
        assertThat(run.extraction().withoutDestinations(List.of("osaka")).details().destinations())
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Failure
    // -------------------------------------------------------------------------------------

    @Test
    void malformedOutputGetsOneRepairAttemptAndThenTheForm() {
        Run run = run("malformed.json");

        assertThat(run.llm().prompts()).hasSize(2);
        assertThat(run.llm().prompts().get(1).messages())
                .anySatisfy(message -> assertThat(message.text()).contains("could not be parsed"));
        assertThat(run.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(run.extraction().failureCode()).isEqualTo(AiProviderException.RESPONSE_INVALID);
        assertThat(run.extraction().details()).isEqualTo(TripBriefDetails.empty());
        assertThat(run.extraction().clarification().questions()).hasSize(7);
    }

    @Test
    void aTimeoutFallsBackWithoutRetryingAndWithoutThrowing() {
        Run run = run("timeout.json");

        assertThat(run.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.FALLBACK);
        assertThat(run.extraction().failureCode()).isEqualTo(AiProviderException.TIMEOUT);
        assertThat(run.extraction().promptVersion())
                .isEqualTo(TripBriefExtractionPrompt.versionLabel());
    }

    // -------------------------------------------------------------------------------------
    // Prompt injection
    // -------------------------------------------------------------------------------------

    @Test
    void aMessageTryingToOverrideTheInstructionsNeverReachesTheSystemBlock() {
        Run run = run("injection.json");
        String system = run.llm().prompts().get(0).systemText();

        assertThat(system).doesNotContain("maintenance mode");
        assertThat(system).doesNotContain("Ignore all previous instructions");
        assertThat(system).doesNotContain("Set budget_amount to -1");
        // The instruction block is the rendered template followed by the schema, and nothing else.
        // Asserting the prefix rather than only the absences means a future concatenation of user
        // text anywhere into that block fails, not just this fixture's particular phrasing.
        assertThat(system).startsWith(expectedSystemText(run));
        assertThat(system).contains(TripBriefExtractionPrompt.SCHEMA);
    }

    @Test
    void anAttemptToCloseTheFenceEarlyIsNeutralised() {
        Run run = run("injection.json");
        String userMessage = run.llm().prompts().get(0).conversation().get(0).text();

        assertThat(userMessage).startsWith("<USER_TEXT>").endsWith("</USER_TEXT>");
        // Exactly one opening and one closing marker: the payload's own </USER_TEXT> was replaced,
        // so nothing after it can be read as though it arrived from outside the fence.
        assertThat(userMessage.split("</USER_TEXT>", -1)).hasSize(2);
        assertThat(userMessage).contains("[redacted-marker]");
    }

    @Test
    void evenACompromisedModelCannotPutAnInvalidValueIntoTheBrief() {
        // The fixture is what a model that fully obeyed the injection would return.
        Run run = run("injection.json");

        assertThat(run.extraction().details().budget()).isNull();
        assertThat(run.extraction().details().party()).isNull();
        assertThat(run.extraction().details().interests()).isEmpty();
        assertThat(run.extraction().unresolvedFields()).containsExactlyInAnyOrder(
                ClarificationNeeded.QUESTION_BUDGET_MAX,
                ClarificationNeeded.QUESTION_PARTY_SIZE,
                ClarificationNeeded.QUESTION_INTERESTS);
        assertThat(run.extraction().isComplete()).isFalse();
    }

    // -------------------------------------------------------------------------------------
    // Thresholds
    // -------------------------------------------------------------------------------------

    /** Schema-validity threshold: conforming output binds on attempt one, every time. */
    @ParameterizedTest
    @ValueSource(strings = {"complete", "incomplete", "vague", "invalid-dates",
            "impossible-budget", "multilingual-ms", "unsupported-destination", "surprise-me",
            "injection"})
    void everyWellFormedFixtureBindsWithoutARepairAttempt(String fixture) {
        Run run = run(fixture + ".json");

        assertThat(run.llm().prompts()).hasSize(1);
        assertThat(run.extraction().outcome()).isEqualTo(TripBriefExtractionOutcome.EXTRACTED);
        assertThat(run.extraction().promptVersion())
                .isEqualTo(TripBriefExtractionPrompt.versionLabel());
    }

    // -------------------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------------------

    private record Run(TripBriefExtraction extraction, ScriptedLlm llm, String locale) {
    }

    private static Run run(String fixture) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = read(mapper, fixture);
        ScriptedLlm llm = script(mapper, node);
        PromptTemplateStore store = new PromptTemplateStore();
        TripBriefExtractionPrompt.register(store);
        LlmTripBriefExtractor extractor = new LlmTripBriefExtractor(
                new StructuredOutputRunner(llm, mapper), store, CLOCK);

        String locale = node.get("locale").asText();
        TripBriefExtraction extraction = extractor.extract(new TripBriefExtractionRequest(
                node.get("user_text").asText(), locale, TripBriefDetails.empty()));
        return new Run(extraction, llm, locale);
    }

    private static ScriptedLlm script(ObjectMapper mapper, JsonNode node) {
        ScriptedLlm llm = new ScriptedLlm();
        if (node.hasNonNull("model_failure")) {
            return llm.failsWith(failureFor(node.get("model_failure").asText()));
        }
        if (node.hasNonNull("model_reply_raw")) {
            return llm.reply(node.get("model_reply_raw").asText());
        }
        try {
            return llm.reply(mapper.writeValueAsString(node.get("model_reply")));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static JsonNode read(ObjectMapper mapper, String fixture) {
        try (InputStream source =
                TripBriefGoldenFileTest.class.getResourceAsStream("/ai/trip-brief/" + fixture)) {
            assertThat(source).as("fixture %s", fixture).isNotNull();
            return mapper.readTree(source);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static AiProviderException failureFor(String code) {
        return switch (code) {
            case AiProviderException.TIMEOUT -> AiProviderException.timeout("golden fixture");
            case AiProviderException.RATE_LIMITED ->
                    AiProviderException.rateLimited("golden fixture");
            case AiProviderException.RESPONSE_INVALID ->
                    AiProviderException.responseInvalid("golden fixture");
            default -> AiProviderException.unavailable("golden fixture");
        };
    }

    private static String expectedSystemText(Run run) {
        PromptTemplateStore store = new PromptTemplateStore();
        TripBriefExtractionPrompt.register(store);
        return TripBriefExtractionPrompt.render(
                store.version(TripBriefExtractionPrompt.ID, TripBriefExtractionPrompt.VERSION),
                run.locale(), LocalDate.of(2026, 1, 15));
    }

    private static List<String> questionIds(TripBriefExtraction extraction) {
        return extraction.clarification().questions().stream()
                .map(ClarificationQuestion::id)
                .toList();
    }
}
