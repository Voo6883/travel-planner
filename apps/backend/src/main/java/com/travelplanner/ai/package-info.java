/**
 * AI layer — provider routing, adapters, prompts, structured output, resilience, and observability
 * (PLAN §5; task 14).
 *
 * <p>LangChain4j types may not appear outside {@code ai.langchain4j} (AGENTS.md). That package is
 * additionally sealed by visibility: every class in it is package-private except one factory whose
 * signatures mention only project ports, so the rule holds at compile time.
 *
 * <p>Feature agents and tools ({@code ai/agent/}) are not here — they arrive with the tasks that own
 * them (19, 21, 22, 25, 30, 35). This package is the runtime they run on.
 */
package com.travelplanner.ai;
