/**
 * Provider routing (PLAN §5.4). {@code LlmClientRouter} is the {@code LlmPort} every feature injects;
 * it selects an adapter per feature and applies retry, the circuit breaker, and {@code ai_call_log}
 * to every call so no adapter has to.
 */
package com.travelplanner.ai.client;
