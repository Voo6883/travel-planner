package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row mapping for {@code research_run_result} (V25). */
@Entity
@Table(name = "research_run_result")
public class ResearchRunResultEntity {

    @Id
    @Column(name = "research_run_id", nullable = false)
    private UUID researchRunId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "no_confident_result", nullable = false)
    private boolean noConfidentResult;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "prompt_template_id", nullable = false, length = 120)
    private String promptTemplateId;

    @Column(name = "prompt_version", nullable = false)
    private int promptVersion;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_json", nullable = false, columnDefinition = "jsonb")
    private String excludedJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ResearchRunResultEntity() {
    }

    public UUID getResearchRunId() {
        return researchRunId;
    }

    public void setResearchRunId(UUID researchRunId) {
        this.researchRunId = researchRunId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isNoConfidentResult() {
        return noConfidentResult;
    }

    public void setNoConfidentResult(boolean noConfidentResult) {
        this.noConfidentResult = noConfidentResult;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public String getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(String promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public int getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(int promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getExcludedJson() {
        return excludedJson;
    }

    public void setExcludedJson(String excludedJson) {
        this.excludedJson = excludedJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
