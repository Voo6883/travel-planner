package com.travelplanner.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI runtime configuration (PLAN §5.4, §9 "model, temperature, token caps, routing per feature in
 * {@code application.yml}").
 *
 * <p>The defaults are the ones that make a fresh checkout and CI work with <strong>no API keys at
 * all</strong>: {@code provider.default=stub}. That is not convenience — the task's "do not require
 * live keys in CI" is expressed as a default so CI cannot drift into needing one. It mirrors what
 * {@code MailProperties} does for Resend.
 *
 * <p>Embeddings are configured separately from chat and have no per-feature routing, because
 * PLAN §5.4 and ADR 010 §5 pin them: chat may switch providers freely, embeddings may not.
 */
@ConfigurationProperties(prefix = "travelplanner.ai")
public class AiProperties {

    /** The value that selects the deterministic in-process adapters. */
    public static final String STUB_PROVIDER = "stub";

    public static final String ANTHROPIC_PROVIDER = "anthropic";
    public static final String OPENAI_PROVIDER = "openai";

    /**
     * Serves recorded provider output instead of calling one (review §6.I).
     *
     * <p>A sibling of {@link #STUB_PROVIDER} rather than a flag on the real providers: it is a distinct
     * adapter with distinct failure behaviour — an unmatched prompt is an error, deliberately, where the
     * stub answers everything. Both are refused under the {@code prod} profile.
     */
    public static final String REPLAY_PROVIDER = "replay";

    private final Provider provider = new Provider();
    private final Anthropic anthropic = new Anthropic();
    private final OpenAi openai = new OpenAi();
    private final Embeddings embeddings = new Embeddings();
    private final Resilience resilience = new Resilience();
    private final Replay replay = new Replay();

    /**
     * Optional per-feature overrides, e.g. {@code research: anthropic} (PLAN §5.4). A feature with
     * no entry uses {@link Provider#getDefaultProvider()}. Unknown provider names fail at startup.
     */
    private Map<String, String> routing = new LinkedHashMap<>();

    public Provider getProvider() {
        return provider;
    }

    public Anthropic getAnthropic() {
        return anthropic;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public Map<String, String> getRouting() {
        return routing;
    }

    public void setRouting(Map<String, String> routing) {
        this.routing = routing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(routing);
    }

    /** Which provider answers by default. */
    public static class Provider {

        private String defaultProvider = STUB_PROVIDER;

        /** Bound from {@code travelplanner.ai.provider.default}. */
        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String value) {
            this.defaultProvider = normalise(value, STUB_PROVIDER);
        }
    }

    /** Settings used only when a provider resolves to {@code anthropic}. */
    public static class Anthropic {

        private String apiKey = "";
        private String model = "claude-sonnet-4-5";
        private String baseUrl = "";
        private Integer maxOutputTokens = 4096;
        private Double temperature = 0.7;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            this.apiKey = value == null ? "" : value.trim();
        }

        public String getModel() {
            return model;
        }

        public void setModel(String value) {
            this.model = normalise(value, "claude-sonnet-4-5");
        }

        /** Blank means the SDK default endpoint; set only for a proxy or a recorded test server. */
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String value) {
            this.baseUrl = value == null ? "" : value.trim();
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer value) {
            this.maxOutputTokens = value;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double value) {
            this.temperature = value;
        }
    }

    /** Settings used only when a provider resolves to {@code openai}. */
    public static class OpenAi {

        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private String baseUrl = "";
        private Integer maxOutputTokens = 4096;
        private Double temperature = 0.7;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            this.apiKey = value == null ? "" : value.trim();
        }

        public String getModel() {
            return model;
        }

        public void setModel(String value) {
            this.model = normalise(value, "gpt-4.1-mini");
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String value) {
            this.baseUrl = value == null ? "" : value.trim();
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer value) {
            this.maxOutputTokens = value;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double value) {
            this.temperature = value;
        }
    }

    /**
     * The vector-index pin (ADR 010 §5).
     *
     * <p>There is one of these and no map, so the configuration is structurally incapable of naming
     * two embedding providers. {@code AiConfigValidator} additionally rejects any model/dimension
     * pair that is not the ADR's pin, so a well-meaning "let's try the large model" edit fails at
     * startup rather than writing 3072-dimensional rows into a 1536-dimensional index.
     */
    public static class Embeddings {

        private String provider = STUB_PROVIDER;
        private String model = "text-embedding-3-small";
        private int dimension = 1536;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String value) {
            this.provider = normalise(value, STUB_PROVIDER);
        }

        public String getModel() {
            return model;
        }

        public void setModel(String value) {
            this.model = normalise(value, "text-embedding-3-small");
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int value) {
            this.dimension = value;
        }
    }

    /** Timeouts, bounded retry, and the circuit-breaker seam (PLAN §9 "Resilience"). */
    public static class Resilience {

        private Duration timeout = Duration.ofSeconds(60);
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double backoffMultiplier = 2.0;
        private int circuitBreakerFailureThreshold = 5;
        private Duration circuitBreakerOpenDuration = Duration.ofSeconds(30);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            this.timeout = value;
        }

        /** Total attempts, not extra ones. {@code 1} disables retry. */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            this.maxAttempts = value;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration value) {
            this.initialBackoff = value;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double value) {
            this.backoffMultiplier = value;
        }

        /** Consecutive failures before the breaker opens for one provider. */
        public int getCircuitBreakerFailureThreshold() {
            return circuitBreakerFailureThreshold;
        }

        public void setCircuitBreakerFailureThreshold(int value) {
            this.circuitBreakerFailureThreshold = value;
        }

        public Duration getCircuitBreakerOpenDuration() {
            return circuitBreakerOpenDuration;
        }

        public void setCircuitBreakerOpenDuration(Duration value) {
            this.circuitBreakerOpenDuration = value;
        }
    }

    public Replay getReplay() {
        return replay;
    }

    /**
     * Provider replay (review §6.I).
     *
     * <p>Two independent switches, because they are opposite operations. Selecting
     * {@link AiProperties#REPLAY_PROVIDER} as a provider <em>reads</em> fixtures; {@code record=true}
     * <em>writes</em> them by wrapping whichever real provider is selected. Combining them into one
     * "replay mode" would make "record against Anthropic" unexpressible, which is the only way to
     * create a fixture in the first place.
     */
    public static class Replay {

        /**
         * Where fixtures live. Defaults under {@code src/test/resources} because that is what a
         * committed, shareable fixture is — test data, not runtime configuration.
         */
        private String directory = "apps/backend/src/test/resources/ai/replay";

        /**
         * Wrap the selected provider and write what it returns.
         *
         * <p>Off by default and refused under {@code prod}. Recording is a development action: model
         * output can echo a prompt back, so a recording made against real traffic can contain a
         * traveller's words inside the completion text even though the prompt itself is only hashed.
         */
        private boolean record;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String value) {
            this.directory = normalise(value, "apps/backend/src/test/resources/ai/replay");
        }

        public boolean isRecord() {
            return record;
        }

        public void setRecord(boolean value) {
            this.record = value;
        }
    }

    private static String normalise(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
