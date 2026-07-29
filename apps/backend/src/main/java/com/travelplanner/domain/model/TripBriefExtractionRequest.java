package com.travelplanner.domain.model;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.List;
import java.util.Locale;

/**
 * One extraction request: what the traveller wrote, in which language, against what the brief
 * already holds (task 19, UC-C1-01).
 *
 * <p>A bundle rather than three parameters on the port, per PLAN §4.0.4 — and it is also where the
 * input bounds live, so no adapter has to remember them.
 *
 * @param userText the traveller's own words. <strong>Untrusted data, never an instruction.</strong>
 *     The adapter puts this in a user message inside a delimiter block; nothing may concatenate it
 *     into the system or schema instructions (PLAN §9 "LLM injection")
 * @param locale {@code en} or {@code ms} — the two languages the product ships (PLAN §11). Anything
 *     else falls back to {@link #DEFAULT_LOCALE}: an unsupported locale is a reason to answer in
 *     English, not a reason to refuse a trip
 * @param known the brief's current details, so extraction merges rather than replaces. Never
 *     {@code null} after construction
 */
public record TripBriefExtractionRequest(String userText, String locale, TripBriefDetails known) {

    /**
     * Longer than this is not a trip description.
     *
     * <p>It is refused rather than truncated. Truncation would silently drop the half of the
     * message that named the budget and then ask the traveller for a budget they had just given,
     * which reads as the product not listening. It also caps the input token cost of a single call
     * at a knowable figure.
     */
    public static final int MAX_USER_TEXT_LENGTH = 4000;

    /** The language used when the requested one is not supported. */
    public static final String DEFAULT_LOCALE = "en";

    /** PLAN §11: English and Malay. */
    public static final List<String> SUPPORTED_LOCALES = List.of("en", "ms");

    public TripBriefExtractionRequest {
        if (userText == null || userText.isBlank()) {
            throw ValidationFailedException.field("user_text", "must not be blank");
        }
        if (userText.length() > MAX_USER_TEXT_LENGTH) {
            throw ValidationFailedException.field("user_text",
                    "must be at most " + MAX_USER_TEXT_LENGTH + " characters");
        }
        locale = normalisedLocale(locale);
        known = known == null ? TripBriefDetails.empty() : known;
    }

    /** A request against a brand-new brief. */
    public static TripBriefExtractionRequest of(String userText, String locale) {
        return new TripBriefExtractionRequest(userText, locale, TripBriefDetails.empty());
    }

    private static String normalisedLocale(String locale) {
        if (locale == null) {
            return DEFAULT_LOCALE;
        }
        String tag = locale.trim().toLowerCase(Locale.ROOT);
        int separator = tag.indexOf('-');
        String language = separator < 0 ? tag : tag.substring(0, separator);
        return SUPPORTED_LOCALES.contains(language) ? language : DEFAULT_LOCALE;
    }
}
