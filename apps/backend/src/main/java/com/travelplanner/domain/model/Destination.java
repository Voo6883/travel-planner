package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.exception.DestinationNotCoveredException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A place the product may know something about (ADR 010 §4, PLAN §4.1.2).
 *
 * <p>Existing in this table is <em>not</em> the same as being covered. A destination row can be
 * created the moment somebody names a city, while {@link #coverageLevel()} says whether anything
 * has actually been curated for it. Keeping those two facts separate is what lets the picker list
 * a place and the ranker refuse it in the same breath.
 *
 * <p><strong>The eligibility rule lives here.</strong> Task 16 requires the domain to express "only
 * FULL destinations enter C2 ranking" rather than leaving it to a {@code WHERE} clause. A query
 * filter is one omission away from silently ranking a half-curated city, and that omission looks
 * like working software right up until a user acts on the result.
 *
 * @param slug the stable handle seeds and URLs use, e.g. {@code tokyo-jp}. Also the partition key
 *        for the per-destination HNSW indexes (ADR 010 §5), so it is not a cosmetic field
 * @param timezone IANA zone. A property of the place rather than of a trip, and required before
 *        any itinerary can put an event on a clock
 * @param latitude absent together with {@code longitude} — a half-set coordinate silently places a
 *        city on the equator, which looks like data rather than like the mistake it is
 */
public record Destination(
        UUID id,
        String slug,
        String name,
        String countryCode,
        String timezone,
        Double latitude,
        Double longitude,
        CoverageLevel coverageLevel) {

    /** Matches {@code destination.slug varchar(120)}. */
    public static final int MAX_SLUG_LENGTH = 120;

    public Destination {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(coverageLevel, "coverageLevel");

        if (slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be 1.." + MAX_SLUG_LENGTH + " characters, got '" + slug + "'");
        }
        if (name.isBlank()) {
            // A blank name reaches the destination picker as an unclickable empty row and the
            // itinerary header as nothing at all. `not null` in the schema does not catch it:
            // PostgreSQL considers '' a perfectly good non-null string.
            throw new IllegalArgumentException("name must not be blank");
        }
        requireIanaZone(timezone);
        // ISO 3166-1 alpha-2, matching char(2) in the schema. Checked here so a three-letter code
        // fails with a message rather than as a truncation.
        if (countryCode.length() != 2) {
            throw new IllegalArgumentException(
                    "countryCode must be ISO 3166-1 alpha-2, got '" + countryCode + "'");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be set together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    /**
     * The timezone has to be a zone the JVM can actually resolve, not merely a non-empty string.
     *
     * <p>Everything C3 does with it happens later and elsewhere: an itinerary converts an event to
     * local time at scheduling time, and {@code ZoneId.of} throws there. By then the bad value is a
     * curated row in the database, the stack trace names the scheduler, and the fix is a data
     * migration. Rejecting {@code "Asia/Tokio"} at construction moves that failure to the seed
     * loader, where the offending file is still on screen.
     *
     * <p>{@code ZoneId.of} rather than a regex, because the set of valid zones is the tzdb the JVM
     * ships and a pattern can only check that a string looks like one. {@code "UTC+7"} looks fine
     * and is not a zone; it is a fixed offset that ignores the DST an itinerary has to respect.
     */
    private static void requireIanaZone(String timezone) {
        try {
            java.time.ZoneId zone = java.time.ZoneId.of(timezone);
            if (!java.time.ZoneId.getAvailableZoneIds().contains(zone.getId())) {
                throw new java.time.DateTimeException("not a region-based zone");
            }
        } catch (java.time.DateTimeException unresolvable) {
            throw new IllegalArgumentException("timezone must be an IANA zone id such as "
                    + "'Asia/Tokyo', got '" + timezone + "'", unresolvable);
        }
    }

    /** ADR 010 §4: only {@code FULL} destinations enter C2 ranking. */
    public boolean isRankingEligible() {
        return coverageLevel.isRankingEligible();
    }

    /**
     * Asserts this destination may be ranked, or refuses with the honest alternative.
     *
     * <p>Call this instead of testing {@link #isRankingEligible()} at the point of use: the
     * refusal needs the supported list, and building it at each call site is how one of them ends
     * up returning a bare "no".
     *
     * @throws DestinationNotCoveredException when coverage is anything but {@code FULL}
     */
    public void requireRankable(List<String> supportedSlugs) {
        if (!isRankingEligible()) {
            throw new DestinationNotCoveredException(slug, supportedSlugs);
        }
    }

    /** Present only when both coordinates were curated. */
    public Optional<Double> latitudeIfKnown() {
        return Optional.ofNullable(latitude);
    }

    public Optional<Double> longitudeIfKnown() {
        return Optional.ofNullable(longitude);
    }
}
