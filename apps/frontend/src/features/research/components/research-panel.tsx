'use client';

import { Alert, Button, Card, Progress } from 'antd';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import { useOnlineStatus } from '@/hooks/use-online-status';
import type { ResearchJob } from '@/lib/api/research-api';
import type { Trip } from '@/lib/api/trip-api';
import { useResearchJob, useResearchTrip, useStartResearch } from '../hooks/use-research-job';
import { useRankedRecommendations, useSelectRecommendation } from '../hooks/use-research-recommendations';
import { RecommendationCard } from './recommendation-card';

export interface ResearchPanelProps {
  tripId: string;
}

/** The failure reasons the platform writes; anything else falls back to a generic message. */
const KNOWN_ERROR_CODES = new Set(['research_timeout', 'research_failed', 'research_interrupted']);

type Translate = ReturnType<typeof useTranslations<'research'>>;

/**
 * Complete C2 research experience (UC-C2-01…06/10/13/16): start, poll, empty, ranked cards,
 * traveler guide, and explicit Plan-this-trip confirmation.
 */
export function ResearchPanel({ tripId }: ResearchPanelProps) {
  const t = useTranslations('research');
  const online = useOnlineStatus();
  const trip = useResearchTrip(tripId);
  const start = useStartResearch();
  const select = useSelectRecommendation();
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useResearchJob(tripId, jobId);
  const ready = trip.data !== undefined && isResultsVisible(trip.data.status);
  const results = useRankedRecommendations(tripId, ready);

  if (trip.isPending) {
    return <LoadingState label={t('panel.title')} rows={3} />;
  }

  if (trip.isError) {
    return <ErrorAlert error={trip.error} onRetry={() => void trip.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-6">
      {!online ? <Alert showIcon type="warning" message={t('offline.title')} description={t('offline.body')} /> : null}
      <Card title={t('panel.title')} className="border-border-subtle">
        <JobBody
          t={t}
          status={trip.data.status}
          job={job.data}
          starting={start.isPending}
          startFailed={start.isError}
          offline={!online}
          onStart={() => {
            void start.mutateAsync({ tripId }).then((started) => setJobId(started.job_id));
          }}
        />
      </Card>
      {ready ? (
        <ResultsBody
          t={t}
          trip={trip.data}
          loading={results.isPending}
          error={results.error}
          onRetry={() => void results.refetch()}
          data={results.data}
          selectingId={select.isPending ? select.variables?.recommendationId : null}
          selectFailed={select.isError}
          offline={!online}
          onPlan={(recommendationId) => {
            void select.mutateAsync({ tripId, recommendationId });
          }}
        />
      ) : null}
    </div>
  );
}

/** @deprecated Prefer {@link ResearchPanel}; kept as the public alias used by the trip page. */
export const ResearchJobPanel = ResearchPanel;
export type ResearchJobPanelProps = ResearchPanelProps;

interface JobBodyProps {
  t: Translate;
  status: Trip['status'];
  job: ResearchJob | undefined;
  starting: boolean;
  startFailed: boolean;
  offline: boolean;
  onStart: () => void;
}

function JobBody({ t, status, job, starting, startFailed, offline, onStart }: JobBodyProps) {
  if (status === 'RESEARCH_QUEUED' || status === 'RESEARCH_RUNNING') {
    return <ActiveState t={t} status={status} job={job} />;
  }
  if (isResultsVisible(status)) {
    return <Alert showIcon type="success" message={t('panel.ready_title')} description={t('panel.ready_help')} />;
  }
  if (status === 'BRIEF_COMPLETE') {
    return (
      <StartState t={t} job={job} starting={starting} startFailed={startFailed} offline={offline} onStart={onStart} />
    );
  }
  return <p className="text-body-sm text-foreground-muted">{t('panel.locked')}</p>;
}

function ActiveState({ t, status, job }: { t: Translate; status: Trip['status']; job: ResearchJob | undefined }) {
  const running = status === 'RESEARCH_RUNNING';
  const percent = job?.status === 'running' || job?.status === 'queued' ? job.progress_pct : 0;

  return (
    <div className="flex flex-col gap-3">
      <div>
        <p className="text-body font-medium text-foreground">
          {running ? t('panel.running_title') : t('panel.queued_title')}
        </p>
        <p className="text-body-sm text-foreground-muted">
          {job ? (running ? t('panel.running_help') : t('panel.queued_help')) : t('panel.in_progress_generic')}
        </p>
      </div>
      <Progress percent={percent} status="active" aria-label={t('panel.progress', { percent })} />
    </div>
  );
}

interface StartStateProps {
  t: Translate;
  job: ResearchJob | undefined;
  starting: boolean;
  startFailed: boolean;
  offline: boolean;
  onStart: () => void;
}

function StartState({ t, job, starting, startFailed, offline, onStart }: StartStateProps) {
  const failed = job?.status === 'failed';
  const hasRun = job !== undefined;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body-sm text-foreground-muted">{t('panel.intro')}</p>
      {failed ? (
        <Alert showIcon type="error" message={t('error.title')} description={t(failureMessageKey(job))} />
      ) : null}
      {startFailed ? <Alert showIcon type="error" message={t('error.start_failed')} /> : null}
      <Button
        type="primary"
        loading={starting}
        disabled={offline || starting}
        onClick={onStart}
        className="min-h-control w-fit"
      >
        {hasRun ? t('panel.rerun') : t('panel.start')}
      </Button>
    </div>
  );
}

interface ResultsBodyProps {
  t: Translate;
  trip: Trip;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  data: ReturnType<typeof useRankedRecommendations>['data'];
  selectingId: string | null | undefined;
  selectFailed: boolean;
  offline: boolean;
  onPlan: (recommendationId: string) => void;
}

function ResultsBody({
  t,
  trip,
  loading,
  error,
  onRetry,
  data,
  selectingId,
  selectFailed,
  offline,
  onPlan,
}: ResultsBodyProps) {
  if (loading) {
    return <LoadingState label={t('results.loading')} rows={4} />;
  }
  if (error || !data) {
    return <ErrorAlert error={error ?? new Error('missing')} onRetry={onRetry} />;
  }
  if (data.no_confident_result || data.recommendations.length === 0) {
    return <EmptyState title={t('results.empty_title')} description={t('results.empty_help')} />;
  }

  const canSelect = trip.status === 'RESEARCH_READY';
  const selectedId = trip.selected_recommendation_id ?? data.selected_recommendation_id ?? null;

  return (
    <div className="flex flex-col gap-4">
      <h2 className="m-0 text-title text-foreground">{t('results.title')}</h2>
      {selectFailed ? <Alert showIcon type="error" message={t('results.select_failed')} /> : null}
      {data.recommendations.map((recommendation) => (
        <RecommendationCard
          key={recommendation.recommendation_id}
          recommendation={recommendation}
          selected={selectedId === recommendation.recommendation_id}
          selecting={selectingId === recommendation.recommendation_id}
          canSelect={canSelect}
          offline={offline}
          onPlan={onPlan}
        />
      ))}
    </div>
  );
}

function failureMessageKey(job: ResearchJob): Parameters<Translate>[0] {
  const code = job.error_code ?? '';
  if (KNOWN_ERROR_CODES.has(code)) {
    return `error.${code}` as Parameters<Translate>[0];
  }
  return 'error.generic';
}

function isResultsVisible(status: Trip['status']): boolean {
  return (
    status === 'RESEARCH_READY' ||
    status === 'DESTINATION_SELECTED' ||
    status === 'ITINERARY_READY' ||
    status === 'BOOKING_IN_PROGRESS' ||
    status === 'BOOKED'
  );
}
