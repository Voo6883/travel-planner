package com.travelplanner.api.controller;

import com.travelplanner.api.dto.HealthResponse;
import com.travelplanner.api.dto.ReadinessResponse;
import com.travelplanner.application.health.ReadinessService;
import com.travelplanner.application.health.ReadinessStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness surface required by PLAN §4.0.0 for Phase 0a.
 *
 * <p>Routing only — the readiness decision belongs to {@link ReadinessService} (PLAN §4.0.1).
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ReadinessService readinessService;

    public HealthController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    /**
     * Liveness. Deliberately free of any dependency check — an unreachable database must not
     * make the container look dead and trigger a restart loop.
     */
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    /** Readiness. 200 when every contributor is ready, 503 otherwise. */
    @GetMapping("/ready")
    public ResponseEntity<ReadinessResponse> ready() {
        ReadinessStatus status = readinessService.evaluate();
        HttpStatus httpStatus = status.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus)
                .body(new ReadinessResponse(status.ready() ? "UP" : "DOWN", status.components()));
    }
}
