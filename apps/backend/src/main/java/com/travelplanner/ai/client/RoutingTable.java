package com.travelplanner.ai.client;

import java.util.Map;

/**
 * Which adapters exist and which feature reaches which one (PLAN §5.4).
 *
 * <p>A value object rather than three constructor parameters on {@link LlmClientRouter}, per
 * PLAN §4.0.4's own remedy for the ≤3-parameter rule. It also makes the routing table testable on
 * its own: a test builds one with two fake ports and asserts on resolution without a Spring context.
 *
 * @param providers adapters by provider name — {@code stub}, {@code anthropic}, {@code openai}. Only
 *     the ones actually selected are constructed, so this map has one or two entries in practice
 * @param routing feature name to provider name; entries naming an absent provider are ignored at
 *     runtime, having already been rejected at startup by {@code AiConfigValidator}
 */
public record RoutingTable(Map<String, LlmProvider> providers, Map<String, String> routing,
        String defaultProvider) {

    public RoutingTable {
        providers = Map.copyOf(providers);
        routing = Map.copyOf(routing);
        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalArgumentException(
                    "The default AI provider '" + defaultProvider + "' has no registered adapter");
        }
    }
}
