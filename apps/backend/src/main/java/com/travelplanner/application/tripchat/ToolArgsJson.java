package com.travelplanner.application.tripchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The parse-time invariants both trip-brief tool argument records share.
 *
 * <p>It exists so that "reject an unknown tool, a non-object, an unknown field, a wrong type" is
 * one implementation rather than two that drift. The rule the harness locks (§4: a state-mutating
 * tool is never invoked from free text) is enforced here — every value is read through a typed
 * accessor that refuses the wrong JSON shape as {@code validation_failed}, never as a runtime cast
 * failure that would surface as {@code 500}.
 *
 * <p><strong>Absent and null are the same fact: "not provided".</strong> An {@code update} is a
 * merge of the fields the model actually stated (task 22 Scope), so a field the model omitted and a
 * field it sent as {@code null} both leave the stored value untouched. {@link #present} is the one
 * place that decision lives, and the optional accessors below all route through it.
 */
final class ToolArgsJson {

    private ToolArgsJson() {
    }

    static JsonNode readObject(String inputJson, ObjectMapper mapper) {
        try {
            String source = inputJson == null || inputJson.isBlank() ? "{}" : inputJson;
            JsonNode root = mapper.readTree(source);
            if (root == null || !root.isObject()) {
                throw failure("tool_args", "must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException invalid) {
            throw failure("tool_args", "must be valid JSON");
        }
    }

    static void rejectUnknownFields(JsonNode node, Set<String> allowed) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw failure(field, "is not a supported field");
            }
        }
    }

    /** A field is provided only when it is present and not JSON null. */
    static boolean present(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull();
    }

    static int requireInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw failure(field, "is required");
        }
        return intOf(value, field);
    }

    static Integer optionalInt(JsonNode node, String field) {
        return present(node, field) ? intOf(node.get(field), field) : null;
    }

    static Boolean optionalBoolean(JsonNode node, String field) {
        if (!present(node, field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (!value.isBoolean()) {
            throw failure(field, "must be a boolean");
        }
        return value.booleanValue();
    }

    static String requireText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw failure(field, "is required");
        }
        return value;
    }

    static String optionalText(JsonNode node, String field) {
        if (!present(node, field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (!value.isTextual()) {
            throw failure(field, "must be a string");
        }
        return value.textValue();
    }

    static JsonNode optionalObject(JsonNode node, String field) {
        if (!present(node, field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (!value.isObject()) {
            throw failure(field, "must be an object");
        }
        return value;
    }

    static JsonNode requireObject(JsonNode node, String field) {
        JsonNode value = optionalObject(node, field);
        if (value == null) {
            throw failure(field, "is required");
        }
        return value;
    }

    static List<String> optionalTextList(JsonNode node, String field) {
        if (!present(node, field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (!value.isArray()) {
            throw failure(field, "must be an array of strings");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isTextual()) {
                throw failure(field, "must be an array of strings");
            }
            values.add(element.textValue());
        }
        return List.copyOf(values);
    }

    /** {@link Money} from a {@code {amount, currency}} object, using the same value object as the form. */
    static Money moneyOf(JsonNode object, String field) {
        String amount = requireText(object, field + ".amount", "amount");
        String currency = requireText(object, field + ".currency", "currency");
        return Money.of(amount, currency);
    }

    /** {@link DateRange} from a {@code {start_date, end_date}} object of ISO-8601 dates. */
    static DateRange dateRangeOf(JsonNode object, String field) {
        LocalDate start = dateOf(object, field + ".start_date", "start_date");
        LocalDate end = dateOf(object, field + ".end_date", "end_date");
        return DateRange.of(start, end);
    }

    /** Resolves an enum constant, reporting a bad value as {@code validation_failed} not {@code 500}. */
    static <E extends Enum<E>> E enumValue(Class<E> vocabulary, String value, String field) {
        try {
            return Enum.valueOf(vocabulary, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw failure(field, "'" + value + "' is not a valid option");
        }
    }

    static ValidationFailedException failure(String field, String message) {
        return ValidationFailedException.field(field, message);
    }

    private static int intOf(JsonNode value, String field) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw failure(field, "must be an integer");
        }
        return value.intValue();
    }

    private static String requireText(JsonNode object, String field, String key) {
        JsonNode value = object.get(key);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw failure(field, "must be a string");
        }
        return value.textValue();
    }

    private static LocalDate dateOf(JsonNode object, String field, String key) {
        String raw = requireText(object, field, key);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException notADate) {
            throw failure(field, "must be an ISO-8601 date");
        }
    }
}
