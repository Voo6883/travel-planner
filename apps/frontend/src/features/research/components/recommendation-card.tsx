'use client';

import { Alert, Button, Card, Tag } from 'antd';
import { useTranslations } from 'next-intl';
import type { RankedRecommendation } from '@/lib/api/research-api';
import { TravelerGuideSections } from './traveler-guide-sections';

export interface RecommendationCardProps {
  recommendation: RankedRecommendation;
  selected: boolean;
  selecting: boolean;
  canSelect: boolean;
  offline: boolean;
  onPlan: (recommendationId: string) => void;
}

/** One ranked destination card with guide, cost, confidence, and Plan CTA (UC-C2-04/06/10). */
export function RecommendationCard({
  recommendation,
  selected,
  selecting,
  canSelect,
  offline,
  onPlan,
}: RecommendationCardProps) {
  const t = useTranslations('research');
  const cost = recommendation.est_cost;
  const confidence = recommendation.score_breakdown.confidence;
  const freshness = recommendation.score_breakdown.freshness_factor;

  return (
    <Card
      className="border-border-subtle"
      title={
        <div className="flex flex-wrap items-center gap-2">
          <span>
            #{recommendation.rank} {recommendation.destination_slug}
          </span>
          {selected ? <Tag color="success">{t('results.selected')}</Tag> : null}
        </div>
      }
      extra={
        <span className="text-body-sm text-foreground-muted">
          {t('results.fit_score', { score: recommendation.fit_score.toFixed(2) })}
        </span>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="m-0 text-body text-foreground">{recommendation.rationale}</p>
        <div className="flex flex-wrap gap-3 text-body-sm text-foreground-muted">
          {cost ? <span>{t('results.est_cost', { amount: cost.amount, currency: cost.currency })}</span> : null}
          <span>{t('results.confidence', { value: Math.round(confidence * 100) })}</span>
          <span>{t('results.freshness', { value: Math.round(freshness * 100) })}</span>
          {recommendation.best_window ? (
            <span>{t('results.best_window', { window: recommendation.best_window })}</span>
          ) : null}
        </div>
        {recommendation.risks.length > 0 ? (
          <Alert type="warning" showIcon message={t('results.risks')} description={recommendation.risks.join(' · ')} />
        ) : null}
        <TravelerGuideSections recommendation={recommendation} />
        {canSelect && !selected ? (
          <Button
            type="primary"
            className="min-h-control w-fit"
            loading={selecting}
            disabled={offline || selecting}
            onClick={() => onPlan(recommendation.recommendation_id)}
          >
            {t('results.plan_cta')}
          </Button>
        ) : null}
        {selected ? <p className="m-0 text-body-sm text-foreground-muted">{t('results.selected_help')}</p> : null}
      </div>
    </Card>
  );
}
