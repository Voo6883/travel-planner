package com.travelplanner.infrastructure.health;

import com.travelplanner.application.health.ReadinessCheck;
import com.travelplanner.application.health.ReadinessContributor;
import org.springframework.stereotype.Component;

/**
 * Placeholder contributor so {@code /api/v1/ready} has a real participant before any external
 * dependency exists.
 *
 * <p>If the Spring context started, the process is serving. This reports UP unconditionally and
 * is expected to be joined — not replaced — by a database contributor in
 * tasks/04-docker-runtime.md / tasks/07-database-domain-foundation.md. Until then readiness is
 * necessarily weaker than liveness, which is documented in the task 02 handoff.
 */
@Component
public class ProcessReadinessContributor implements ReadinessContributor {

    @Override
    public String name() {
        return "process";
    }

    @Override
    public ReadinessCheck check() {
        return ReadinessCheck.up();
    }
}
