'use client';

import { Collapse, List, Typography } from 'antd';
import { useTranslations } from 'next-intl';
import type { RankedRecommendation } from '@/lib/api/research-api';

type Translate = ReturnType<typeof useTranslations<'research'>>;

interface TravelerGuideSectionsProps {
  recommendation: RankedRecommendation;
}

/** Expandable traveler-guide sections on a recommendation card (UC-C2-10/13/16). */
export function TravelerGuideSections({ recommendation }: TravelerGuideSectionsProps) {
  const t = useTranslations('research');
  const guide = recommendation.traveler_guide;

  const items = [
    section(t, 'guide.overview', guide.overview),
    section(t, 'guide.why_now', guide.why_now),
    listSection(t, 'guide.areas', guide.areas),
    section(t, 'guide.food', guide.food),
    listSection(t, 'guide.highlights', guide.highlights),
    section(t, 'guide.practical', guide.practical),
    section(t, 'guide.mobility', guide.mobility),
    appPackSection(t, guide.local_app_pack),
    sourcesSection(t, recommendation),
  ].filter((item): item is NonNullable<typeof item> => item !== null);

  return <Collapse items={items} bordered={false} className="bg-transparent" />;
}

function section(t: Translate, key: Parameters<Translate>[0], body: string | null | undefined) {
  if (!body) {
    return null;
  }
  return {
    key,
    label: t(key),
    children: <Typography.Paragraph className="mb-0 text-body-sm">{body}</Typography.Paragraph>,
  };
}

function listSection(t: Translate, key: Parameters<Translate>[0], values: string[]) {
  if (values.length === 0) {
    return null;
  }
  return {
    key,
    label: t(key),
    children: (
      <List
        size="small"
        dataSource={values}
        renderItem={(item) => <List.Item className="border-0 px-0 py-1 text-body-sm">{item}</List.Item>}
      />
    ),
  };
}

function appPackSection(t: Translate, pack: RankedRecommendation['traveler_guide']['local_app_pack']) {
  if (pack.length === 0) {
    return null;
  }
  return {
    key: 'guide.apps',
    label: t('guide.apps'),
    children: (
      <List
        size="small"
        dataSource={pack}
        renderItem={(app) => (
          <List.Item className="border-0 px-0 py-1 text-body-sm">
            <span className="font-medium text-foreground">{app.name}</span>
            <span className="text-foreground-muted"> — {app.usage}</span>
          </List.Item>
        )}
      />
    ),
  };
}

function sourcesSection(t: Translate, recommendation: RankedRecommendation) {
  const refs = recommendation.source_refs;
  if (refs.length === 0) {
    return null;
  }
  return {
    key: 'guide.sources',
    label: t('guide.sources'),
    children: (
      <List
        size="small"
        dataSource={refs}
        renderItem={(ref) => (
          <List.Item className="border-0 px-0 py-1 text-body-sm">
            <span className="font-medium text-foreground">{ref.field_group}</span>
            <span className="text-foreground-muted"> — {ref.source_ref}</span>
          </List.Item>
        )}
      />
    ),
  };
}
