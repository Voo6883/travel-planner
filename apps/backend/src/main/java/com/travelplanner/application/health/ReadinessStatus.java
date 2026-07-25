package com.travelplanner.application.health;

import java.util.Map;

/**
 * Aggregated readiness across all contributors.
 *
 * @param ready      true only when every contributor is ready
 * @param components contributor name to reported status
 */
public record ReadinessStatus(boolean ready, Map<String, String> components) {}
