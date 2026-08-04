package com.travelplanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.agent.StubTravelResearchAgent;
import com.travelplanner.ai.tool.KnowledgeResearchTools;
import com.travelplanner.ai.tool.ResearchToolJson;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.port.TravelResearchAgentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the C2 travel research agent (task 25).
 *
 * <p>The deterministic {@link StubTravelResearchAgent} is the default and the only implementation
 * until a live-LLM narrative path is selected via provider config. It is published as
 * {@link TravelResearchAgentPort} so {@code application/} never imports {@code ai/}.
 *
 * <p>Beans are conditional on a datasource because tools require {@link KnowledgePort}, which only
 * exists when the persistence adapter is wired.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
public class ResearchAgentConfig {

    @Bean
    public ResearchToolJson researchToolJson(ObjectMapper objectMapper) {
        return new ResearchToolJson(objectMapper);
    }

    @Bean
    @ConditionalOnBean(KnowledgePort.class)
    public KnowledgeResearchTools knowledgeResearchTools(
            KnowledgePort knowledge, ResearchToolJson json) {
        return new KnowledgeResearchTools(knowledge, json);
    }

    /**
     * Stub agent wins for CI and fresh checkouts (default AI provider is stub). A future live
     * narrative agent would be a second {@code @Bean} with a stronger {@code @ConditionalOnProperty}
     * on {@code travelplanner.ai.provider.default} — not a replacement that breaks the no-keys rule.
     */
    @Bean
    @ConditionalOnBean(KnowledgeResearchTools.class)
    public TravelResearchAgentPort travelResearchAgentPort(
            KnowledgeResearchTools tools,
            ResearchToolJson json,
            ResearchProperties properties) {
        return new StubTravelResearchAgent(
                tools, json, properties.getMaxToolCalls(), properties.getMaxTokens());
    }
}
