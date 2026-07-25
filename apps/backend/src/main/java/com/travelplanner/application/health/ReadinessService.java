package com.travelplanner.application.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Evaluates every registered {@link ReadinessContributor}.
 *
 * <p>Business logic lives here rather than in the controller (PLAN §4.0.1). A contributor
 * that throws is treated as not ready rather than propagating a 500 — readiness must report,
 * not fail.
 */
@Service
public class ReadinessService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessService.class);

    private final List<ReadinessContributor> contributors;

    public ReadinessService(List<ReadinessContributor> contributors) {
        this.contributors = contributors;
    }

    public ReadinessStatus evaluate() {
        Map<String, String> components = new LinkedHashMap<>();
        boolean allReady = true;

        for (ReadinessContributor contributor : contributors) {
            ReadinessCheck result = evaluateOne(contributor);
            components.put(contributor.name(), describe(result));
            if (!result.ready()) {
                allReady = false;
            }
        }
        return new ReadinessStatus(allReady, components);
    }

    private ReadinessCheck evaluateOne(ReadinessContributor contributor) {
        try {
            return contributor.check();
        } catch (RuntimeException exception) {
            log.warn("Readiness contributor '{}' threw; reporting as not ready", contributor.name(), exception);
            return ReadinessCheck.down("check failed");
        }
    }

    private String describe(ReadinessCheck result) {
        if (result.ready()) {
            return "UP";
        }
        return result.detail() == null ? "DOWN" : "DOWN: " + result.detail();
    }
}
