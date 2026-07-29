package com.travelplanner.api.controller;

import com.travelplanner.api.dto.trip.AnswerClarificationRequest;
import com.travelplanner.api.dto.trip.TripBriefResponse;
import com.travelplanner.api.dto.trip.UpdateTripBriefRequest;
import com.travelplanner.application.trip.AnswerClarificationCommand;
import com.travelplanner.application.trip.SaveTripBriefCommand;
import com.travelplanner.application.trip.TripBriefService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C1 intake over HTTP — the deterministic half (PLAN §4.1.3, UC-C1-01, UC-C1-04).
 *
 * <h2>Three operations, and why they are these three</h2>
 *
 * <table>
 *   <caption>Brief endpoints</caption>
 *   <tr><th>Operation</th><th>Shape</th><th>Why</th></tr>
 *   <tr><td>Read</td><td>{@code GET .../brief}</td>
 *       <td>Returns the {@code version} every write must echo (ADR 008 §1)</td></tr>
 *   <tr><td>Save the form</td><td>{@code PUT .../brief}</td>
 *       <td>The debounced editor holds the whole brief and sends the whole brief</td></tr>
 *   <tr><td>Answer a question</td><td>{@code POST .../brief/actions/answer-clarification}</td>
 *       <td>ADR 008 §3 — a typed diff, so answering one question cannot clobber eight fields</td></tr>
 * </table>
 *
 * <p>There is no {@code PATCH}, here or anywhere (PLAN §6.1). PLAN §4.1.3 sketched the answer path
 * as {@code PUT .../brief/clarification}; ADR 008 §3 supersedes that with the action above, for the
 * reason the ADR gives — re-sending a whole aggregate to answer one question does not scale, and
 * each field it re-sends is a field the agent may have changed since the form last read it.
 *
 * <h2>Every mutation returns the new full resource</h2>
 *
 * <p>ADR 008 §2. The response carries the saved brief, its incremented version, the recomputed
 * clarification, and the trip status the save moved the trip to — so a form that has just
 * auto-saved holds exactly what the server holds and does not have to refetch into its own next
 * keystroke.
 *
 * <h2>Routing only</h2>
 *
 * <p>Ownership, versions, coverage, and status transitions are all decided in
 * {@code application/trip/} (PLAN §4.0). This class binds and delegates.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/brief")
@RequiresDatabase
public class TripBriefController {

    private final TripBriefService briefs;

    public TripBriefController(TripBriefService briefs) {
        this.briefs = briefs;
    }

    /** The brief, the trip's status, and the outstanding questions. {@code 404} unless it is the caller's. */
    @GetMapping
    public TripBriefResponse get(@PathVariable UUID tripId,
            @AuthenticationPrincipal UserContext caller) {
        return TripBriefResponse.from(briefs.get(tripId, caller));
    }

    /**
     * Replaces every editable field.
     *
     * <p>{@code 409 version_conflict} with {@code details.current_version} on a stale
     * {@code expected_version}; {@code 404 destination_not_covered} with {@code details.supported}
     * when a named destination has no curated knowledge (ADR 010 §4).
     */
    @PutMapping
    public TripBriefResponse save(@PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripBriefRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return TripBriefResponse.from(briefs.save(
                new SaveTripBriefCommand(tripId, request.expectedVersion(), request.toDetails()),
                caller));
    }

    /**
     * Answers one or more outstanding questions and re-validates (UC-C1-04 step 4).
     *
     * <p>An answer to a question that is not outstanding, or one carrying the wrong value type, is
     * {@code 400 validation_failed} rather than a silently ignored field — a dropped answer is
     * indistinguishable to the user from one the server accepted and then lost.
     */
    @PostMapping("/actions/answer-clarification")
    public TripBriefResponse answerClarification(@PathVariable UUID tripId,
            @Valid @RequestBody AnswerClarificationRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return TripBriefResponse.from(briefs.answerClarification(
                new AnswerClarificationCommand(tripId, request.expectedVersion(),
                        request.toAnswers()),
                caller));
    }
}
