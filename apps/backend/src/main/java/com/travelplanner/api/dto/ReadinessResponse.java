package com.travelplanner.api.dto;

import java.util.Map;

/**
 * Readiness payload.
 *
 * @param status     {@code "UP"} or {@code "DOWN"}
 * @param components per-contributor status; carries no credentials or stack traces
 */
public record ReadinessResponse(String status, Map<String, String> components) {}
