package com.travelplanner.ai.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.ai.prompt.PromptTemplate;
import com.travelplanner.ai.prompt.PromptTemplateStore;
import com.travelplanner.domain.enums.TravelInterest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The prompt-change gate (task 19 Definition of Done: "prompt changes are covered by an evaluation
 * gate"; AI-AGENT-WORKFLOW A7).
 *
 * <h2>How the gate works</h2>
 *
 * <p>{@link #theRenderedInstructionBlockMatchesTheCheckedInDigest()} pins the SHA-256 of the
 * <em>rendered</em> instruction block for a fixed locale and date against
 * {@code src/test/resources/ai/trip-brief/prompt-v1.sha256}. Editing the template text, or adding a
 * {@link TravelInterest} that the prompt enumerates, changes that digest and fails the build. The
 * only way through is to update the digest file — which cannot be done without also running
 * {@code TripBriefGoldenFileTest}, because both live in the same task and the same source set.
 *
 * <p>Rendered rather than raw, on purpose. The raw template still says {@code {{interests}}}, so
 * hashing it would miss the case where the domain vocabulary changed underneath a prompt that reads
 * identically — the most likely way for extraction to start emitting a constant nobody accepts.
 *
 * <p>The digest is per version: a v2 prompt gets {@code prompt-v2.sha256} beside this one, and the
 * v1 file stays, so an {@code ai_call_log} row naming v1 can still be reproduced.
 */
class TripBriefExtractionPromptTest {

    /** Fixed inputs, so the digest is a property of the prompt rather than of the calendar. */
    private static final LocalDate PINNED_DAY = LocalDate.of(2026, 1, 15);
    private static final String PINNED_LOCALE = "en";

    private final PromptTemplateStore store = new PromptTemplateStore();

    @Test
    void theRenderedInstructionBlockMatchesTheCheckedInDigest() {
        TripBriefExtractionPrompt.register(store);

        String actual = sha256(rendered());

        assertThat(actual)
                .as("""
                        The extraction prompt changed. A prompt is behaviour, so this is not a \
                        formatting diff: bump TripBriefExtractionPrompt.VERSION, add \
                        prompt-v<n>.sha256 beside the old one, and re-run \
                        TripBriefGoldenFileTest to confirm extraction still behaves.""")
                .isEqualTo(expectedDigest());
    }

    @Test
    void theVersionLabelIsTheValueCarriedOnEveryExtractionAndLoggedRow() {
        assertThat(TripBriefExtractionPrompt.versionLabel())
                .isEqualTo(TripBriefExtractionPrompt.ID + "@v" + TripBriefExtractionPrompt.VERSION);
    }

    @Test
    void theTemplateIsResolvableByExactVersionSoAnOldLogRowStaysReproducible() {
        TripBriefExtractionPrompt.register(store);

        PromptTemplate pinned = store.version(TripBriefExtractionPrompt.ID,
                TripBriefExtractionPrompt.VERSION);

        assertThat(pinned.version()).isEqualTo(TripBriefExtractionPrompt.VERSION);
        assertThat(store.latest(TripBriefExtractionPrompt.ID)).isEqualTo(pinned);
        assertThat(store.ids()).contains(TripBriefExtractionPrompt.ID);
    }

    /** The system block has no slot for user text, and cannot be given one by a caller. */
    @Test
    void theTemplateDeclaresNoVariableThatCouldCarryUserText() {
        TripBriefExtractionPrompt.register(store);

        assertThat(store.latest(TripBriefExtractionPrompt.ID).variables()).containsExactlyInAnyOrder(
                "today", "locale", "question_ids", "flexibilities", "paces", "interests");
    }

    @Test
    void renderingWithAnUnexpectedVariableIsRefusedRatherThanIgnored() {
        TripBriefExtractionPrompt.register(store);
        PromptTemplate template = store.latest(TripBriefExtractionPrompt.ID);

        assertThatThrownBy(() -> template.render(Map.of("user_text", "smuggled")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRenderedBlockNamesTheDomainVocabulariesRatherThanAHandTypedCopy() {
        TripBriefExtractionPrompt.register(store);

        String instructions = rendered();

        assertThat(instructions).contains(TravelInterest.EXPERIENCES.name());
        assertThat(instructions).contains("FLEXIBLE_MONTH").contains("PACKED");
        assertThat(instructions).contains("budget_max").contains("party_size");
        assertThat(instructions).contains("2026-01-15");
    }

    private String rendered() {
        return TripBriefExtractionPrompt.render(
                store.version(TripBriefExtractionPrompt.ID, TripBriefExtractionPrompt.VERSION),
                PINNED_LOCALE, PINNED_DAY);
    }

    private static String expectedDigest() {
        String resource = "/ai/trip-brief/prompt-v" + TripBriefExtractionPrompt.VERSION + ".sha256";
        try (InputStream source = TripBriefExtractionPromptTest.class.getResourceAsStream(resource)) {
            assertThat(source).as("digest file %s", resource).isNotNull();
            return new String(source.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
