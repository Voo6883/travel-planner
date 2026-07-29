package com.travelplanner.api.controller;

import com.travelplanner.api.dto.trip.ArchiveTripRequest;
import com.travelplanner.api.dto.trip.CreateTripRequest;
import com.travelplanner.api.dto.trip.RenameTripRequest;
import com.travelplanner.api.dto.trip.TripListResponse;
import com.travelplanner.api.dto.trip.TripResponse;
import com.travelplanner.application.trip.ArchiveTripCommand;
import com.travelplanner.application.trip.CreateTripCommand;
import com.travelplanner.application.trip.RenameTripCommand;
import com.travelplanner.application.trip.TripService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trip aggregate over HTTP (PLAN §3.1, USE-CASES "Trip status").
 *
 * <h2>The caller is the owner, always</h2>
 *
 * <p>No route here accepts a user id. {@code @AuthenticationPrincipal} supplies the caller from the
 * signed session cookie and a live database read, and {@link TripService} scopes every query by it
 * (PLAN §4.0.2-L). A trip belonging to somebody else answers {@code 404 not_found}, identical to
 * one that does not exist, so this surface cannot be used to discover that another user's trip
 * exists.
 *
 * <p>{@code SecurityConfig} carries no allow-list entry for {@code /api/v1/trips/**}, so the
 * default {@code anyRequest().authenticated()} applies. That is the safe direction: an endpoint
 * added here is protected unless somebody deliberately opens it.
 *
 * <h2>Routing only</h2>
 *
 * <p>Every rule lives in {@code application/trip/} (PLAN §4.0: "Controller routes only; Service
 * owns logic"). This class binds, delegates, and maps — it makes no decision about ownership,
 * versions, or status.
 */
@RestController
@RequestMapping("/api/v1/trips")
@RequiresDatabase
public class TripController {

    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    /** The caller's trips, newest first. */
    @GetMapping
    public TripListResponse list(@AuthenticationPrincipal UserContext caller) {
        return TripListResponse.from(trips.list(caller));
    }

    /** {@code 404 not_found} for an id that is not the caller's, existing or otherwise. */
    @GetMapping("/{tripId}")
    public TripResponse get(@PathVariable UUID tripId,
            @AuthenticationPrincipal UserContext caller) {
        return TripResponse.from(trips.get(tripId, caller));
    }

    /**
     * Creates a {@code DRAFT} trip and its empty brief.
     *
     * <p>{@code 201} with the trip in the body, so the client has the {@code version} its first
     * brief save will have to echo without a second request (ADR 008 §1).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@Valid @RequestBody CreateTripRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return TripResponse.from(trips.create(new CreateTripCommand(request.name()), caller));
    }

    /**
     * Renames the trip. {@code 409 version_conflict} with {@code details.current_version} when
     * {@code expected_version} is stale (ADR 008 §2); the response otherwise carries the new full
     * resource with its incremented version.
     */
    @PutMapping("/{tripId}")
    public TripResponse rename(@PathVariable UUID tripId,
            @Valid @RequestBody RenameTripRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return TripResponse.from(trips.rename(
                new RenameTripCommand(tripId, request.expectedVersion(), request.name()), caller));
    }

    /**
     * Makes the trip read-only for every actor, the agent included.
     *
     * <p>A typed action rather than a status field on {@code PUT} (ADR 008 §3): archiving is not a
     * field edit, and giving it the same request shape as a rename is how one gets sent by mistake.
     */
    @PostMapping("/{tripId}/actions/archive")
    public TripResponse archive(@PathVariable UUID tripId,
            @Valid @RequestBody ArchiveTripRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return TripResponse.from(trips.archive(
                new ArchiveTripCommand(tripId, request.expectedVersion()), caller));
    }

    /**
     * Deletes the trip and, by cascade, its brief. {@code 204}, with nothing in the body — there is
     * no resource left to describe.
     *
     * <p>No {@code expected_version}: see {@link TripService#delete}. A body on {@code DELETE} is
     * also something intermediaries are entitled to drop, so a version carried there would be a
     * concurrency control that silently stops working behind some proxies.
     */
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable UUID tripId,
            @AuthenticationPrincipal UserContext caller) {
        trips.delete(tripId, caller);
        return ResponseEntity.noContent().build();
    }
}
