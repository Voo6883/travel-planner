package com.travelplanner.ai.tool;

import java.util.Objects;

/**
 * Caps tool-call volume and approximate tokens for one research run (PLAN §4.1, AI-AGENT-WORKFLOW A5).
 *
 * <p>Honours {@link Thread#interrupt()} so the research-job platform's 90s wall-clock cancel can
 * stop a runaway loop without waiting for the next I/O.
 */
public final class ResearchToolBudget {

    private final int maxToolCalls;
    private final int maxTokens;
    private int toolCalls;
    private int tokens;

    public ResearchToolBudget(int maxToolCalls, int maxTokens) {
        if (maxToolCalls < 1 || maxTokens < 1) {
            throw new IllegalArgumentException("budgets must be positive");
        }
        this.maxToolCalls = maxToolCalls;
        this.maxTokens = maxTokens;
    }

    public String consume(String toolName, String argumentsJson, String resultJson) {
        Objects.requireNonNull(toolName, "toolName");
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("research tool loop interrupted");
        }
        if (toolCalls >= maxToolCalls) {
            throw new IllegalStateException("research max tool calls exceeded (" + maxToolCalls + ")");
        }
        toolCalls++;
        tokens += estimate(argumentsJson) + estimate(resultJson);
        if (tokens > maxTokens) {
            throw new IllegalStateException("research token budget exceeded (" + maxTokens + ")");
        }
        return resultJson;
    }

    public int toolCallsUsed() {
        return toolCalls;
    }

    public int tokensUsed() {
        return tokens;
    }

    private static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
