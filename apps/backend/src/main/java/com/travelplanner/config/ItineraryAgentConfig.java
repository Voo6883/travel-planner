package com.travelplanner.config;

import com.travelplanner.ai.agent.StubItineraryAgent;
import com.travelplanner.domain.port.ItineraryAgentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the C3 agent as a domain port, so {@code application/} never imports {@code ai/} —
 * the same boundary {@code ResearchAgentConfig} maintains for C2 and ArchUnit enforces for both.
 *
 * <p>The stub wins for CI and for a fresh checkout, because PLAN's rule is that the suite runs with
 * no provider keys. A live narrative agent would be a second {@code @Bean} with a stronger
 * {@code @ConditionalOnProperty} on {@code travelplanner.ai.provider.default} — an addition, not a
 * replacement, so the no-keys path keeps working. See **F-52**, which records the same standing
 * situation for the research agent.
 *
 * <p>Unlike the research agent this needs no datasource: the agent is handed its candidates by the
 * service and reaches no port itself, which is exactly what makes "cannot invent a POI" structural.
 */
@Configuration(proxyBeanMethods = false)
public class ItineraryAgentConfig {

    @Bean
    @ConditionalOnProperty(name = "travelplanner.ai.itinerary-agent.enabled",
            havingValue = "true", matchIfMissing = true)
    public ItineraryAgentPort itineraryAgentPort() {
        return new StubItineraryAgent();
    }
}
