package com.travelplanner.api.controller;

import com.travelplanner.api.dto.research.ResearchJobResponse;
import com.travelplanner.application.research.ResearchJobService;
import com.travelplanner.application.research.StartResearchCommand;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C2 research jobs over HTTP (UC-C2-01/02, PLAN §3.1).
 *
 * <h2>Start returns immediately, never holds the run</h2>
 *
 * <p>{@code POST .../research/run} answers {@code 202 Accepted} with the queued job — the run
 * happens on a background worker and the client polls (the task's Do-not list: do not hold an HTTP
 * connection for the full job). The trip moves to {@code RESEARCH_QUEUED} in the same transaction
 * that persists the job, before any dispatch.
 *
 * <h2>Routing only</h2>
 *
 * <p>Ownership, the {@code BRIEF_COMPLETE} gate, the duplicate-active refusal, and every status
 * transition live in {@link ResearchJobService} (PLAN §4.0). This class binds and delegates.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/research")
@RequiresDatabase
public class ResearchController {

    private final ResearchJobService research;

    public ResearchController(ResearchJobService research) {
        this.research = research;
    }

    /**
     * Starts a research run (UC-C2-01). {@code 202} with the queued job; {@code 400 validation_failed}
     * when the trip is not {@code BRIEF_COMPLETE} or a job is already active; {@code 404} when the
     * trip is not the caller's.
     */
    @PostMapping("/run")
    public ResponseEntity<ResearchJobResponse> run(@PathVariable UUID tripId,
            @AuthenticationPrincipal UserContext caller) {
        ResearchJobResponse body = ResearchJobResponse.from(
                research.start(new StartResearchCommand(tripId), caller));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * Polls a research job (UC-C2-02). {@code 404} when the trip is not the caller's or the job is
     * not that trip's — indistinguishable, so the poll cannot probe for another user's data.
     */
    @GetMapping("/jobs/{jobId}")
    public ResearchJobResponse get(@PathVariable UUID tripId, @PathVariable UUID jobId,
            @AuthenticationPrincipal UserContext caller) {
        return ResearchJobResponse.from(research.get(tripId, jobId, caller));
    }
}
