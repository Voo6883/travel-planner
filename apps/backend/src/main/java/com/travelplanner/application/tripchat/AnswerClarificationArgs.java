package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.model.ClarificationAnswer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validated {@code answer_clarification} tool arguments — the model-facing twin of
 * {@code AnswerClarificationCommand} (task 22, UC-C5-02).
 *
 * <p>Each answer is a typed diff of exactly one field, matching the OpenAPI
 * {@code ClarificationAnswer}: one of {@code text}, {@code number}, {@code money},
 * {@code date_range}, {@code choice}, or {@code choices}, and exactly one. The "exactly one" rule
 * is enforced twice, and neither is redundant: here, so a model sending two values gets a
 * {@code validation_failed} naming the shape it got wrong, and again in
 * {@link ClarificationAnswer}'s own constructor, so the invariant holds for any other caller. The
 * factories on that record are the only way an answer is built, so a value can never land in the
 * wrong slot.
 *
 * <p>Whether the question is actually outstanding, and whether its type matches, is not decided
 * here — {@code ClarificationNeeded.applyAnswers} owns that, because only it knows which questions
 * a given brief still has. This class refuses malformed JSON; that class refuses answers to
 * questions nobody asked.
 */
public record AnswerClarificationArgs(int expectedVersion, List<ClarificationAnswer> answers,
        String inputJson) {

    private static final String EXPECTED_VERSION = "expected_version";
    private static final String ANSWERS = "answers";
    private static final String QUESTION_ID = "question_id";
    private static final String TEXT = "text";
    private static final String NUMBER = "number";
    private static final String MONEY = "money";
    private static final String DATE_RANGE = "date_range";
    private static final String CHOICE = "choice";
    private static final String CHOICES = "choices";

    private static final Set<String> ALLOWED = Set.of(EXPECTED_VERSION, ANSWERS);
    private static final Set<String> ANSWER_ALLOWED = Set.of(QUESTION_ID, TEXT, NUMBER, MONEY,
            DATE_RANGE, CHOICE, CHOICES);
    private static final List<String> VALUE_FIELDS = List.of(TEXT, NUMBER, MONEY, DATE_RANGE,
            CHOICE, CHOICES);

    public AnswerClarificationArgs {
        if (inputJson == null || inputJson.isBlank()) {
            inputJson = "{}";
        }
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    public static AnswerClarificationArgs parse(String toolName, String inputJson, ObjectMapper mapper) {
        if (!TripChatTools.ANSWER_CLARIFICATION.equals(toolName)) {
            throw ToolArgsJson.failure("tool_name", "unknown trip tool: " + toolName);
        }
        JsonNode root = ToolArgsJson.readObject(inputJson, mapper);
        ToolArgsJson.rejectUnknownFields(root, ALLOWED);
        int expectedVersion = ToolArgsJson.requireInt(root, EXPECTED_VERSION);
        return new AnswerClarificationArgs(expectedVersion, answers(root), inputJson);
    }

    /** The question ids this call answered, for the tool result's applied summary. */
    public List<String> answeredQuestionIds() {
        return answers.stream().map(ClarificationAnswer::questionId).toList();
    }

    private static List<ClarificationAnswer> answers(JsonNode root) {
        JsonNode array = root.get(ANSWERS);
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw ToolArgsJson.failure(ANSWERS, "must be a non-empty array");
        }
        List<ClarificationAnswer> parsed = new ArrayList<>();
        for (JsonNode element : array) {
            parsed.add(answerOf(element));
        }
        return List.copyOf(parsed);
    }

    private static ClarificationAnswer answerOf(JsonNode element) {
        if (element == null || !element.isObject()) {
            throw ToolArgsJson.failure(ANSWERS, "each answer must be an object");
        }
        ToolArgsJson.rejectUnknownFields(element, ANSWER_ALLOWED);
        String questionId = ToolArgsJson.requireText(element, QUESTION_ID);
        String slot = onlyValueField(element);
        return build(questionId, slot, element);
    }

    private static String onlyValueField(JsonNode element) {
        List<String> present = VALUE_FIELDS.stream()
                .filter(field -> ToolArgsJson.present(element, field))
                .toList();
        if (present.size() != 1) {
            throw ToolArgsJson.failure(ANSWERS, "each answer must carry exactly one value");
        }
        return present.get(0);
    }

    private static ClarificationAnswer build(String questionId, String slot, JsonNode element) {
        return switch (slot) {
            case TEXT -> ClarificationAnswer.ofText(questionId, ToolArgsJson.requireText(element, TEXT));
            case NUMBER -> ClarificationAnswer.ofNumber(questionId, ToolArgsJson.requireInt(element, NUMBER));
            case MONEY -> ClarificationAnswer.ofMoney(questionId,
                    ToolArgsJson.moneyOf(ToolArgsJson.requireObject(element, MONEY), MONEY));
            case DATE_RANGE -> ClarificationAnswer.ofDateRange(questionId,
                    ToolArgsJson.dateRangeOf(ToolArgsJson.requireObject(element, DATE_RANGE), DATE_RANGE));
            case CHOICE -> ClarificationAnswer.ofChoice(questionId, ToolArgsJson.requireText(element, CHOICE));
            default -> ClarificationAnswer.ofChoices(questionId, ToolArgsJson.optionalTextList(element, CHOICES));
        };
    }
}
