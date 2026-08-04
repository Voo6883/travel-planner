package com.travelplanner.api.dto.research;

import com.travelplanner.api.dto.trip.MoneyPayload;
import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.util.List;
import java.util.UUID;

/**
 * Latest research outcome for a trip (UC-C2-03/04/05/10).
 *
 * <p>When {@code no_confident_result} is true the recommendations list is empty — typed empty,
 * never a fabricated apology.
 */
public record RankedRecommendationsResponse(
        UUID tripId,
        UUID researchRunId,
        boolean noConfidentResult,
        String algorithmVersion,
        UUID selectedRecommendationId,
        List<RankedRecommendationResponse> recommendations) {

    public static RankedRecommendationsResponse from(ResearchRunResult run, UUID selectedId) {
        return new RankedRecommendationsResponse(
                run.tripId(),
                run.researchRunId(),
                run.noConfidentResult(),
                run.algorithmVersion(),
                selectedId,
                run.recommendations().stream().map(RankedRecommendationResponse::from).toList());
    }

    /** One scored destination card. */
    public record RankedRecommendationResponse(
            UUID recommendationId,
            UUID destinationId,
            String destinationSlug,
            String countryCode,
            int rank,
            double fitScore,
            ScoreBreakdownResponse scoreBreakdown,
            MoneyPayload estCost,
            String rationale,
            TravelerGuideResponse travelerGuide,
            List<String> risks,
            String bestWindow,
            List<SourceRefResponse> sourceRefs,
            String algorithmVersion) {

        static RankedRecommendationResponse from(RankedRecommendation row) {
            ScoreBreakdown breakdown = row.breakdown();
            return new RankedRecommendationResponse(
                    row.id(),
                    row.destinationId(),
                    row.destinationSlug(),
                    row.countryCode(),
                    row.rank(),
                    breakdown.fitScore(),
                    ScoreBreakdownResponse.from(breakdown),
                    MoneyPayload.from(breakdown.estimatedCost()),
                    row.rationale(),
                    TravelerGuideResponse.from(row.travelerGuide()),
                    row.risks(),
                    row.bestWindow(),
                    row.sourceRefs().stream().map(SourceRefResponse::from).toList(),
                    row.algorithmVersion());
        }
    }

    /** DSA score terms persisted so the UI never recomputes fit. */
    public record ScoreBreakdownResponse(
            double interestMatch,
            double seasonalityFit,
            double priceFit,
            double areaCoverage,
            double freshnessFactor,
            double confidence,
            double fitScore) {

        static ScoreBreakdownResponse from(ScoreBreakdown breakdown) {
            return new ScoreBreakdownResponse(
                    breakdown.interestMatch(),
                    breakdown.seasonalityFit(),
                    breakdown.priceFit(),
                    breakdown.areaCoverage(),
                    breakdown.freshnessFactor(),
                    breakdown.confidence(),
                    breakdown.fitScore());
        }
    }

    /** Traveler guide sections on a recommendation card (PLAN §4.1). */
    public record TravelerGuideResponse(
            String overview,
            String whyNow,
            List<String> areas,
            String food,
            List<String> highlights,
            String mobility,
            String practical,
            List<LocalAppPackEntryResponse> localAppPack,
            List<SourceRefResponse> sourceRefs) {

        static TravelerGuideResponse from(TravelerGuide guide) {
            return new TravelerGuideResponse(
                    guide.overview(),
                    guide.whyNow(),
                    guide.areas(),
                    guide.food(),
                    guide.highlights(),
                    guide.mobility(),
                    guide.practical(),
                    guide.localAppPack().stream().map(LocalAppPackEntryResponse::from).toList(),
                    guide.sourceRefs().stream().map(SourceRefResponse::from).toList());
        }
    }

    public record LocalAppPackEntryResponse(String usage, String name, String slug) {

        static LocalAppPackEntryResponse from(TravelerGuide.LocalAppPackEntry entry) {
            return new LocalAppPackEntryResponse(entry.usage(), entry.name(), entry.slug());
        }
    }

    public record SourceRefResponse(String sourceRef, String sourceUrl, String fieldGroup) {

        static SourceRefResponse from(RecommendationSourceRef ref) {
            return new SourceRefResponse(ref.sourceRef(), ref.sourceUrl(), ref.fieldGroup());
        }
    }
}
