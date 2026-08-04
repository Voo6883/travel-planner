package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row mapping for {@code ranked_recommendation} (V25). */
@Entity
@Table(name = "ranked_recommendation")
public class RankedRecommendationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "research_run_id", nullable = false)
    private UUID researchRunId;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "destination_slug", nullable = false, length = 120)
    private String destinationSlug;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "rank", nullable = false)
    private int rank;

    /** {@code numeric(12,8)} — scores stay decimal, never binary float (PLAN §4.0.2-A). */
    @Column(name = "fit_score", nullable = false, precision = 12, scale = 8)
    private BigDecimal fitScore;

    @Column(name = "interest_match", nullable = false, precision = 8, scale = 6)
    private BigDecimal interestMatch;

    @Column(name = "seasonality_fit", nullable = false, precision = 8, scale = 6)
    private BigDecimal seasonalityFit;

    @Column(name = "price_fit", nullable = false, precision = 8, scale = 6)
    private BigDecimal priceFit;

    @Column(name = "area_coverage", nullable = false, precision = 8, scale = 6)
    private BigDecimal areaCoverage;

    @Column(name = "freshness_factor", nullable = false, precision = 8, scale = 6)
    private BigDecimal freshnessFactor;

    @Column(name = "confidence", nullable = false, precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column(name = "est_cost_amount", precision = 19, scale = 4)
    private BigDecimal estCostAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "est_cost_currency", length = 3)
    private String estCostCurrency;

    @Column(name = "rationale", nullable = false, columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "traveler_guide", nullable = false, columnDefinition = "jsonb")
    private String travelerGuideJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risks", nullable = false, columnDefinition = "jsonb")
    private String risksJson;

    @Column(name = "best_window", length = 120)
    private String bestWindow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", nullable = false, columnDefinition = "jsonb")
    private String sourceRefsJson;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RankedRecommendationEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public UUID getResearchRunId() {
        return researchRunId;
    }

    public void setResearchRunId(UUID researchRunId) {
        this.researchRunId = researchRunId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public String getDestinationSlug() {
        return destinationSlug;
    }

    public void setDestinationSlug(String destinationSlug) {
        this.destinationSlug = destinationSlug;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public BigDecimal getFitScore() {
        return fitScore;
    }

    public void setFitScore(BigDecimal fitScore) {
        this.fitScore = fitScore;
    }

    public BigDecimal getInterestMatch() {
        return interestMatch;
    }

    public void setInterestMatch(BigDecimal interestMatch) {
        this.interestMatch = interestMatch;
    }

    public BigDecimal getSeasonalityFit() {
        return seasonalityFit;
    }

    public void setSeasonalityFit(BigDecimal seasonalityFit) {
        this.seasonalityFit = seasonalityFit;
    }

    public BigDecimal getPriceFit() {
        return priceFit;
    }

    public void setPriceFit(BigDecimal priceFit) {
        this.priceFit = priceFit;
    }

    public BigDecimal getAreaCoverage() {
        return areaCoverage;
    }

    public void setAreaCoverage(BigDecimal areaCoverage) {
        this.areaCoverage = areaCoverage;
    }

    public BigDecimal getFreshnessFactor() {
        return freshnessFactor;
    }

    public void setFreshnessFactor(BigDecimal freshnessFactor) {
        this.freshnessFactor = freshnessFactor;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public BigDecimal getEstCostAmount() {
        return estCostAmount;
    }

    public void setEstCostAmount(BigDecimal estCostAmount) {
        this.estCostAmount = estCostAmount;
    }

    public String getEstCostCurrency() {
        return estCostCurrency;
    }

    public void setEstCostCurrency(String estCostCurrency) {
        this.estCostCurrency = estCostCurrency;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getTravelerGuideJson() {
        return travelerGuideJson;
    }

    public void setTravelerGuideJson(String travelerGuideJson) {
        this.travelerGuideJson = travelerGuideJson;
    }

    public String getRisksJson() {
        return risksJson;
    }

    public void setRisksJson(String risksJson) {
        this.risksJson = risksJson;
    }

    public String getBestWindow() {
        return bestWindow;
    }

    public void setBestWindow(String bestWindow) {
        this.bestWindow = bestWindow;
    }

    public String getSourceRefsJson() {
        return sourceRefsJson;
    }

    public void setSourceRefsJson(String sourceRefsJson) {
        this.sourceRefsJson = sourceRefsJson;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
