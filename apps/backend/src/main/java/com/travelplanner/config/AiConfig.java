package com.travelplanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.client.LlmClientRouter;
import com.travelplanner.ai.client.RouterSupport;
import com.travelplanner.ai.client.RoutingTable;
import com.travelplanner.ai.extraction.LlmTripBriefExtractor;
import com.travelplanner.ai.extraction.TripBriefExtractionPrompt;
import com.travelplanner.ai.langchain4j.LangChain4jProviderFactory;
import com.travelplanner.ai.observability.AiCallRecorder;
import com.travelplanner.ai.prompt.PromptTemplateStore;
import com.travelplanner.ai.resilience.AiRetryPolicy;
import com.travelplanner.ai.resilience.CircuitBreakerGate;
import com.travelplanner.ai.resilience.CountingCircuitBreakerGate;
import com.travelplanner.ai.stub.StubEmbeddingAdapter;
import com.travelplanner.ai.stub.StubLlmAdapter;
import com.travelplanner.ai.structured.StructuredOutputRunner;
import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.port.AiCallLogPort;
import com.travelplanner.domain.port.EmbeddingPort;
import com.travelplanner.domain.port.LlmPort;
import com.travelplanner.domain.port.TripBriefExtractionPort;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the AI platform (task 14; PLAN §5).
 *
 * <p><strong>Adapters are constructed here, not discovered as beans.</strong> A {@code @Component} on
 * {@code AnthropicLlmAdapter} would make Spring instantiate it whenever the class is on the
 * classpath, and it would then need a {@code @ConditionalOnProperty} that somebody could weaken.
 * Building only the selected providers keeps the "no live keys in CI" rule structural: with the
 * default {@code stub} provider, the vendor adapter classes are never instantiated, so nothing can
 * reach the network and no build can begin requiring a credential.
 *
 * <p>Validation runs first, in this constructor. A misconfigured AI setup fails the context — an
 * application that starts and then fails every chat message is strictly worse than one that refuses
 * to start with a message naming the knob to turn.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    private final AiProperties properties;

    /**
     * A field rather than a shared {@code Clock} bean. No other package in the application publishes
     * one, and introducing a global bean here would be a change to how every future task gets the
     * time — a decision this task's scope does not cover. Unit tests inject their own by constructing
     * the collaborators directly.
     */
    private final Clock clock = Clock.systemUTC();

    public AiConfig(AiProperties properties, Environment environment) {
        AiConfigValidator.validate(properties, environment.getActiveProfiles());
        this.properties = properties;
        log.info("AI platform: default provider={} embeddings={}/{} routing={}",
                properties.getProvider().getDefaultProvider(),
                properties.getEmbeddings().getProvider(),
                properties.getEmbeddings().getModel(),
                properties.getRouting());
    }

    /**
     * The bean features inject. Typed as {@link LlmPort} so nothing outside this class depends on the
     * router's concrete type, which is what keeps a future change of routing strategy invisible.
     */
    @Bean
    public LlmPort llmPort(AiCallRecorder recorder, CircuitBreakerGate breaker) {
        return new LlmClientRouter(routingTable(),
                new RouterSupport(AiRetryPolicy.of(properties.getResilience()), breaker, recorder));
    }

    /**
     * Only the selected providers are built. {@code stub} is always registered so a routing entry
     * can name it explicitly, and so the map is never empty.
     */
    private RoutingTable routingTable() {
        Map<String, LlmPort> providers = new LinkedHashMap<>();
        providers.put(AiProperties.STUB_PROVIDER, new StubLlmAdapter());
        for (String name : selectedProviders()) {
            if (AiProperties.ANTHROPIC_PROVIDER.equals(name)) {
                providers.put(name, LangChain4jProviderFactory.anthropic(
                        properties.getAnthropic(), properties.getResilience().getTimeout()));
            } else if (AiProperties.OPENAI_PROVIDER.equals(name)) {
                providers.put(name, LangChain4jProviderFactory.openAi(
                        properties.getOpenai(), properties.getResilience().getTimeout()));
            }
        }
        return new RoutingTable(providers, properties.getRouting(),
                properties.getProvider().getDefaultProvider());
    }

    private java.util.Set<String> selectedProviders() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.add(properties.getProvider().getDefaultProvider());
        names.addAll(properties.getRouting().values());
        return names;
    }

    /**
     * Exactly one embedding bean, always. The configuration cannot express a second, and
     * {@code AiConfigValidator} has already rejected a model/dimension pair that would produce
     * vectors incomparable with the index (ADR 010 §5).
     */
    @Bean
    public EmbeddingPort embeddingPort() {
        AiProperties.Embeddings embeddings = properties.getEmbeddings();
        if (AiProperties.OPENAI_PROVIDER.equals(embeddings.getProvider())) {
            return LangChain4jProviderFactory.openAiEmbeddings(embeddings,
                    properties.getOpenai().getApiKey(), properties.getResilience().getTimeout());
        }
        return new StubEmbeddingAdapter(embeddings.getDimension());
    }

    @Bean
    public CircuitBreakerGate circuitBreakerGate() {
        return new CountingCircuitBreakerGate(properties.getResilience(), clock);
    }

    @Bean
    public AiCallRecorder aiCallRecorder(AiCallLogPort callLog) {
        return new AiCallRecorder(callLog, clock);
    }

    /**
     * The fallback when no database is configured — the unit suite, and any slice test that does not
     * start a datasource. Discarding the row is correct there: the alternative is that every test
     * touching the AI platform needs Postgres, which is exactly what
     * {@code ./gradlew test} must never require.
     */
    @Bean
    @ConditionalOnMissingBean(AiCallLogPort.class)
    public AiCallLogPort noOpAiCallLogPort() {
        return (AiCallRecord record) -> log.debug("ai_call_log (no datasource): feature={} tokens={}",
                record.feature(), record.usage().totalTokens());
    }

    /**
     * The registry task 14 shipped empty and later tasks fill. Registration happens here, in the
     * bean method, rather than in each feature's own {@code @PostConstruct}: a store that is fully
     * populated the moment it is published cannot be observed half-built by a bean that was created
     * earlier in the graph, and every prompt in the system is listed in one readable place.
     */
    @Bean
    public PromptTemplateStore promptTemplateStore() {
        PromptTemplateStore store = new PromptTemplateStore();
        TripBriefExtractionPrompt.register(store);
        return store;
    }

    @Bean
    public StructuredOutputRunner structuredOutputRunner(LlmPort llmPort, ObjectMapper objectMapper) {
        return new StructuredOutputRunner(llmPort, objectMapper);
    }

    /**
     * C1 extraction (task 19). Constructed here with the rest of the AI platform so
     * {@code application/} depends on {@code TripBriefExtractionPort} and never on {@code ai/} —
     * the boundary {@code LayerRulesTest.applicationDependsOnDomainOnly} enforces.
     */
    @Bean
    public TripBriefExtractionPort tripBriefExtractionPort(StructuredOutputRunner runner,
            PromptTemplateStore prompts) {
        return new LlmTripBriefExtractor(runner, prompts, clock);
    }
}
