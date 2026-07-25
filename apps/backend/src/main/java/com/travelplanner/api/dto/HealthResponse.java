package com.travelplanner.api.dto;

/**
 * Liveness payload.
 *
 * @param status always {@code "UP"} — reaching this handler is the liveness signal
 */
public record HealthResponse(String status) {}
