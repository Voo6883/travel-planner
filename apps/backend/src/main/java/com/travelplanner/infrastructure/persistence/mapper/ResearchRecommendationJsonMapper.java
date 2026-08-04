package com.travelplanner.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.domain.algorithm.ranking.ExcludedDestination;
import com.travelplanner.domain.algorithm.ranking.ExclusionReason;
import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import com.travelplanner.infrastructure.persistence.entity.RankedRecommendationEntity;
import com.travelplanner.infrastructure.persistence.entity.ResearchRunResultEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Manual JSON ↔ domain mapping for research outcomes (V25). MapStruct is a poor fit for jsonb
 * blobs that must round-trip through Jackson without leaking Jackson types into {@code domain/}.
 */
@Component
public class ResearchRecommendationJsonMapper {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new TypeReference<>() { };

    private final ObjectMapper mapper;

    public ResearchRecommendationJsonMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String writeExcluded(List<ExcludedDestination> excluded) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExcludedDestination item : excluded) {
            rows.add(Map.of(
                    "destination_id", item.destinationId().toString(),
                    "slug", item.slug(),
                    "reason", item.reason().name()));
        }
        return write(rows);
    }

    public List<ExcludedDestination> readExcluded(String json) {
        List<Map<String, Object>> rows = readList(json);
        List<ExcludedDestination> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(ExcludedDestination.of(
                    UUID.fromString(String.valueOf(row.get("destination_id"))),
                    String.valueOf(row.get("slug")),
                    ExclusionReason.valueOf(String.valueOf(row.get("reason")))));
        }
        return out;
    }

    public void applyRecommendation(RankedRecommendation domain, RankedRecommendationEntity entity) {
        ScoreBreakdown breakdown = domain.breakdown();
        entity.setId(domain.id());
        entity.setTripId(domain.tripId());
        entity.setUserId(domain.userId());
        entity.setResearchRunId(domain.researchRunId());
        entity.setDestinationId(domain.destinationId());
        entity.setDestinationSlug(domain.destinationSlug());
        entity.setCountryCode(domain.countryCode());
        entity.setRank(domain.rank());
        entity.setFitScore(decimal(breakdown.fitScore(), 8));
        entity.setInterestMatch(decimal(breakdown.interestMatch(), 6));
        entity.setSeasonalityFit(decimal(breakdown.seasonalityFit(), 6));
        entity.setPriceFit(decimal(breakdown.priceFit(), 6));
        entity.setAreaCoverage(decimal(breakdown.areaCoverage(), 6));
        entity.setFreshnessFactor(decimal(breakdown.freshnessFactor(), 6));
        entity.setConfidence(decimal(breakdown.confidence(), 6));
        breakdown.estimatedCostIfPresent().ifPresentOrElse(cost -> {
            entity.setEstCostAmount(cost.amount());
            entity.setEstCostCurrency(cost.currency().getCurrencyCode());
        }, () -> {
            entity.setEstCostAmount(null);
            entity.setEstCostCurrency(null);
        });
        entity.setRationale(domain.rationale());
        entity.setTravelerGuideJson(write(domain.travelerGuide()));
        entity.setRisksJson(write(domain.risks()));
        entity.setBestWindow(domain.bestWindow());
        entity.setSourceRefsJson(write(domain.sourceRefs()));
        entity.setAlgorithmVersion(domain.algorithmVersion());
        entity.setCreatedAt(domain.createdAt());
    }

    public RankedRecommendation toRecommendation(RankedRecommendationEntity entity) {
        Money estimated = null;
        if (entity.getEstCostAmount() != null && entity.getEstCostCurrency() != null) {
            estimated = Money.of(entity.getEstCostAmount(),
                    Currency.getInstance(entity.getEstCostCurrency()));
        }
        ScoreBreakdown breakdown = new ScoreBreakdown(
                asDouble(entity.getInterestMatch()),
                asDouble(entity.getSeasonalityFit()),
                asDouble(entity.getPriceFit()),
                asDouble(entity.getAreaCoverage()),
                asDouble(entity.getFreshnessFactor()),
                asDouble(entity.getConfidence()),
                asDouble(entity.getFitScore()),
                estimated);
        return new RankedRecommendation(
                entity.getId(),
                entity.getTripId(),
                entity.getUserId(),
                entity.getResearchRunId(),
                entity.getDestinationId(),
                entity.getDestinationSlug(),
                entity.getCountryCode(),
                entity.getRank(),
                breakdown,
                entity.getRationale(),
                read(entity.getTravelerGuideJson(), TravelerGuide.class),
                readListOfStrings(entity.getRisksJson()),
                entity.getBestWindow(),
                readSourceRefs(entity.getSourceRefsJson()),
                entity.getAlgorithmVersion(),
                entity.getCreatedAt());
    }

    public void applyRunResult(
            com.travelplanner.domain.model.ResearchRunResult domain,
            ResearchRunResultEntity entity) {
        entity.setResearchRunId(domain.researchRunId());
        entity.setTripId(domain.tripId());
        entity.setUserId(domain.userId());
        entity.setNoConfidentResult(domain.noConfidentResult());
        entity.setAlgorithmVersion(domain.algorithmVersion());
        entity.setPromptTemplateId(domain.promptTemplateId());
        entity.setPromptVersion(domain.promptVersion());
        entity.setModelName(domain.modelName());
        entity.setExcludedJson(writeExcluded(domain.excluded()));
        entity.setCreatedAt(domain.createdAt());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to serialise research json", failure);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to deserialise research json", failure);
        }
    }

    private List<Map<String, Object>> readList(String json) {
        try {
            return mapper.readValue(json == null ? "[]" : json, LIST_OF_MAPS);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to deserialise excluded json", failure);
        }
    }

    private List<String> readListOfStrings(String json) {
        try {
            return mapper.readValue(json == null ? "[]" : json, new TypeReference<List<String>>() { });
        } catch (Exception failure) {
            throw new IllegalStateException("failed to deserialise risks json", failure);
        }
    }

    private List<RecommendationSourceRef> readSourceRefs(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<RecommendationSourceRef>>() { });
        } catch (Exception failure) {
            throw new IllegalStateException("failed to deserialise source_refs json", failure);
        }
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static double asDouble(BigDecimal value) {
        return value.doubleValue();
    }
}
