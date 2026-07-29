package com.travelplanner.api.dto.destination;

import com.travelplanner.domain.model.Destination;

/**
 * One fully covered destination (ADR 010 §4). Serialised snake_case by the global Jackson strategy,
 * so the wire field is {@code country_code}.
 *
 * <p><strong>No id.</strong> {@code destination.id} is a surrogate key and stays server-side. The
 * public handle is the {@code slug}: it is what the seed files, the URLs, and the partial HNSW
 * indexes are keyed on, and it is what {@code destination_not_covered} already lists in
 * {@code details.supported}. Publishing a UUID beside it would give clients two ways to name the
 * same place and guarantee that half of them pick the one this API does not accept back.
 *
 * <p>Contrast with {@code AdminUserSummaryResponse}, which does publish {@code user_id}: an account
 * has no stable public handle, so the surrogate key is the only thing an administrator can address
 * it by. A destination has one.
 *
 * @param slug the stable handle, e.g. {@code tokyo-jp}
 * @param timezone IANA zone — the picker needs it to show local time before any trip exists
 * @param coordinates null when the destination was curated without a position
 */
public record SupportedDestinationResponse(
        String slug,
        String name,
        String countryCode,
        String timezone,
        GeoCoordinatesResponse coordinates) {

    public static SupportedDestinationResponse from(Destination destination) {
        return new SupportedDestinationResponse(
                destination.slug(),
                destination.name(),
                destination.countryCode(),
                destination.timezone(),
                GeoCoordinatesResponse.from(destination));
    }
}
