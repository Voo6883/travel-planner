package com.travelplanner.domain.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral call settings (PLAN §5.4: "keep {@code LlmOptions} provider-neutral; map to
 * vendor params inside each adapter").
 *
 * <p>Nothing here names a vendor. {@code maxOutputTokens} is Anthropic's {@code max_tokens} and
 * OpenAI's {@code max_completion_tokens}; a caller that had to know which is which would defeat the
 * abstraction the moment the router switched providers mid-feature.
 *
 * <p>Built rather than constructed: seven optional settings cannot be a ≤3-parameter call, and a
 * seven-argument constructor is the shape where {@code null, null, 0.7, null} silently means the
 * wrong thing. Every field is nullable-means-"use the configured default", so a feature overrides
 * only what it actually cares about.
 */
public final class LlmOptions {

    /** The routing key used when a caller expresses no feature preference. */
    public static final String DEFAULT_FEATURE = "default";

    private final String feature;
    private final String model;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Duration timeout;
    private final List<String> stopSequences;

    private LlmOptions(Builder builder) {
        this.feature = builder.feature;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.timeout = builder.timeout;
        this.stopSequences = List.copyOf(builder.stopSequences);
    }

    /** Everything defaulted: the configured default provider, model, and limits. */
    public static LlmOptions defaults() {
        return builder().build();
    }

    /** Routes to whichever provider {@code travelplanner.ai.routing.<feature>} names. */
    public static LlmOptions forFeature(String feature) {
        return builder().feature(feature).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Never {@code null} — an unset feature is {@link #DEFAULT_FEATURE}. */
    public String feature() {
        return feature;
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public Duration timeout() {
        return timeout;
    }

    public List<String> stopSequences() {
        return stopSequences;
    }

    /** Mutable builder; not thread-safe, and not meant to be shared. */
    public static final class Builder {

        private String feature = DEFAULT_FEATURE;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private Duration timeout;
        private List<String> stopSequences = List.of();

        private Builder() {
        }

        public Builder feature(String value) {
            this.feature = value == null || value.isBlank() ? DEFAULT_FEATURE : value.trim();
            return this;
        }

        public Builder model(String value) {
            this.model = value;
            return this;
        }

        public Builder temperature(Double value) {
            if (value != null && (value < 0.0 || value > 2.0)) {
                throw new IllegalArgumentException("temperature must be within [0, 2]");
            }
            this.temperature = value;
            return this;
        }

        public Builder topP(Double value) {
            if (value != null && (value <= 0.0 || value > 1.0)) {
                throw new IllegalArgumentException("topP must be within (0, 1]");
            }
            this.topP = value;
            return this;
        }

        public Builder maxOutputTokens(Integer value) {
            if (value != null && value <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
            this.maxOutputTokens = value;
            return this;
        }

        /** Per-call override of the configured provider timeout. */
        public Builder timeout(Duration value) {
            if (value != null && (value.isZero() || value.isNegative())) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = value;
            return this;
        }

        public Builder stopSequences(List<String> value) {
            this.stopSequences = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public LlmOptions build() {
            Objects.requireNonNull(feature, "feature");
            return new LlmOptions(this);
        }
    }
}
