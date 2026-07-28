package com.travelplanner.ai.structured;

/**
 * Pulls the JSON object out of a model reply.
 *
 * <p>Models are told to answer with bare JSON and mostly do — but "mostly" is the problem. They wrap
 * it in a {@code ```json} fence, or open with "Sure, here's the itinerary:". Failing those responses
 * would burn a repair attempt and, often enough, the whole call, on output that was correct in every
 * way that mattered.
 *
 * <p>The scan is brace-balanced and string-aware rather than a regex, because a JSON object
 * containing a brace inside a string value — a POI named {@code "Café {Old Town}"} — defeats a naive
 * match, and the failure would look like a model problem rather than a parsing one.
 */
final class JsonExtractor {

    private JsonExtractor() {
    }

    /** @return the first balanced {@code {...}} span, or the trimmed input when none is found */
    static String extract(String raw) {
        if (raw == null) {
            return "";
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            // Hand the original text to the parser: its error message names what was actually
            // returned, which is what the repair attempt feeds back to the model.
            return raw.trim();
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
            } else if (current == '"') {
                inString = !inString;
            } else if (!inString && current == '{') {
                depth++;
            } else if (!inString && current == '}' && --depth == 0) {
                return raw.substring(start, index + 1);
            }
        }
        return raw.trim();
    }
}
