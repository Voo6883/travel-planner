package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.AppReplacementReason;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "The app you already have does not work here — install this one instead" (tasks/17, table
 * {@code travel_app_replacement} from V21).
 *
 * <p>The negative half of a country's app pack, and the half {@link TravelApp} alone cannot express.
 * A pack listing Didi is useful; a pack that lists Didi <em>and</em> leaves the traveller's existing
 * Uber unmentioned is how somebody stands at an airport opening an app with no cars in it.
 *
 * <p><strong>{@link #replacedAppKey()} is a slug, not a reference to another row.</strong>
 * {@code travel_app} is a table of apps to install in a country, and Uber-in-China is the opposite
 * of that — a globally-held app this market does not support. Seeding it there would create a row
 * whose only purpose is never to be shown, and every pack query would then have to remember to
 * exclude it. The migration header spells this out at length; the short version is that one
 * forgotten {@code WHERE} would recommend installing the app the row exists to warn about.
 *
 * @param localAppId the {@link TravelApp} to recommend instead. Mandatory: a row here means
 *        "install this one", so a suppression with no alternative is not a fact this type carries
 * @param replacedAppKey stable, global, lower-case slug — {@code uber}, {@code whatsapp},
 *        {@code google-maps}. Not country-scoped, because {@code uber} means the same product
 *        everywhere and that is what makes the suppression join possible
 * @param replacedAppName what to show on screen. Stored rather than derived, because title-casing
 *        {@code google-maps} yields "Google-Maps" and, sooner or later, "Whatsapp"
 * @param detail the traveller-facing sentence. The specific reason is worth more than the category —
 *        "Uber does not operate in mainland China" beats "not available" — and writing it is the
 *        point at which a curator checks whether it is still true
 */
public record TravelAppReplacement(
        UUID id,
        UUID localAppId,
        String replacedAppKey,
        String replacedAppName,
        AppReplacementReason reason,
        String detail,
        KnowledgeProvenance provenance) {

    /** Matches {@code travel_app_replacement.replaced_app_key varchar(120)}. */
    public static final int MAX_KEY_LENGTH = 120;

    /**
     * Mirrors {@code ck_travel_app_replacement_key_is_slug}.
     *
     * <p>Validated here as well as in the database because the key is a join target: {@code "Uber "}
     * suppresses nothing at all while looking entirely correct in a seed file, and the failure is
     * silent — the pack renders, and the warning the row exists to produce is simply absent.
     */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public TravelAppReplacement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(localAppId, "localAppId");
        Objects.requireNonNull(replacedAppKey, "replacedAppKey");
        Objects.requireNonNull(replacedAppName, "replacedAppName");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(provenance, "provenance");

        if (replacedAppKey.length() > MAX_KEY_LENGTH || !SLUG.matcher(replacedAppKey).matches()) {
            throw new IllegalArgumentException("replacedAppKey must be a lower-case slug of at most "
                    + MAX_KEY_LENGTH + " characters, got '" + replacedAppKey + "'");
        }
        if (replacedAppName.isBlank()) {
            throw new IllegalArgumentException("replacedAppName must not be blank");
        }
        // A warning with no sentence in it is a red box with nothing to read. The category alone
        // cannot carry the market-specific fact, which is the only part a traveller can act on.
        if (detail.isBlank()) {
            throw new IllegalArgumentException(
                    "detail must not be blank — it is the sentence the traveller reads");
        }
    }

    /**
     * Whether the replaced app is unusable rather than merely a poor choice.
     *
     * <p>Delegates to the reason so the rule lives with the type that carries it, and so a caller
     * cannot re-derive it slightly differently — which is how the same suppression ends up rendered
     * as a hard warning on one screen and a hint on another.
     */
    public boolean replacedAppIsUnusable() {
        return reason.isUnusable();
    }

    /** Does this row suppress {@code key}? Case-insensitive, because callers pass user-facing text. */
    public boolean suppresses(String key) {
        return key != null && replacedAppKey.equalsIgnoreCase(key.trim());
    }
}
