'use client';

import { Alert, Button, Card, Progress } from 'antd';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import type { ResearchJob } from '@/lib/api/research-api';
import type { Trip } from '@/lib/api/trip-api';
import { useResearchJob, useResearchTrip, useStartResearch } from '../hooks/use-research-job';

export interface ResearchJobPanelProps {
  tripId: string;
}

/** The failure reasons the platform writes; anything else falls back to a generic message. */
const KNOWN_ERROR_CODES = new Set(['research_timeout', 'research_failed', 'research_interrupted']);

type Translate = ReturnType<typeof useTranslations<'research'>>;

/**
 * The C2 research affordance on the trip detail screen (UC-C2-01/02).
 *
 * It reads trip status to decide what to show — a start control at `BRIEF_COMPLETE`, live progress
 * while the run is `RESEARCH_QUEUED`/`RESEARCH_RUNNING`, and a done note at `RESEARCH_READY` — and
 * polls the started job for progress. It deliberately does not render recommendations; task 25 owns
 * that surface.
 */
export function ResearchJobPanel({ tripId }: ResearchJobPanelProps) {
  const t = useTranslations('research');
  const trip = useResearchTrip(tripId);
  const start = useStartResearch();
  const [jobId, setJobId] = useState<string | null>(null);
  const job = useResearchJob(tripId, jobId);

  if (trip.isPending) {
    return <LoadingState label={t('panel.title')} rows={3} />;
  }

  if (trip.isError) {
    return <ErrorAlert error={trip.error} onRetry={() => void trip.refetch()} />;
  }

  return (
    <Card title={t('panel.title')} className="border-border-subtle">
      <Body
        t={t}
        status={trip.data.status}
        job={job.data}
        starting={start.isPending}
        startFailed={start.isError}
        onStart={() => {
          void start.mutateAsync({ tripId }).then((started) => setJobId(started.job_id));
        }}
      />
    </Card>
  );
}

interface BodyProps {
  t: Translate;
  status: Trip['status'];
  job: ResearchJob | undefined;
  starting: boolean;
  startFailed: boolean;
  onStart: () => void;
}

function Body({ t, status, job, starting, startFailed, onStart }: BodyProps) {
  if (status === 'RESEARCH_QUEUED' || status === 'RESEARCH_RUNNING') {
    return <ActiveState t={t} status={status} job={job} />;
  }
  if (isResearchDone(status)) {
    return <Alert showIcon type="success" message={t('panel.ready_title')} description={t('panel.ready_help')} />;
  }
  if (status === 'BRIEF_COMPLETE') {
    return <StartState t={t} job={job} starting={starting} startFailed={startFailed} onStart={onStart} />;
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
  onStart: () => void;
}

function StartState({ t, job, starting, startFailed, onStart }: StartStateProps) {
  const failed = job?.status === 'failed';
  const hasRun = job !== undefined;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body-sm text-foreground-muted">{t('panel.intro')}</p>
      {failed ? (
        <Alert showIcon type="error" message={t('error.title')} description={t(failureMessageKey(job))} />
      ) : null}
      {startFailed ? <Alert showIcon type="error" message={t('error.start_failed')} /> : null}
      <Button type="primary" loading={starting} onClick={onStart} className="min-h-control w-fit">
        {hasRun ? t('panel.rerun') : t('panel.start')}
      </Button>
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

/** A trip that has reached research-ready or moved past it into selection/itinerary/booking. */
function isResearchDone(status: Trip['status']): boolean {
  return (
    status === 'RESEARCH_READY' ||
    status === 'DESTINATION_SELECTED' ||
    status === 'ITINERARY_READY' ||
    status === 'BOOKING_IN_PROGRESS' ||
    status === 'BOOKED'
  );
}
