'use client';

import { CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined } from '@ant-design/icons';
import { Tag } from 'antd';
import { useTranslations } from 'next-intl';

export type SaveState = 'idle' | 'saving' | 'saved' | 'error';

export interface SaveStatusProps {
  state: SaveState;
}

/**
 * Auto-save is user-visible state (§6.2). The tag keeps screen-reader text and visual state in the
 * same component so a background save is never silent.
 */
export function SaveStatus({ state }: SaveStatusProps) {
  const t = useTranslations('trip_brief');

  return (
    <Tag icon={iconForState(state)} color={colorForState(state)} aria-live="polite">
      {t(`save.${state}`)}
    </Tag>
  );
}

function iconForState(state: SaveState) {
  if (state === 'saving') {
    return <LoadingOutlined />;
  }
  if (state === 'saved') {
    return <CheckCircleOutlined />;
  }
  if (state === 'error') {
    return <CloseCircleOutlined />;
  }
  return null;
}

function colorForState(state: SaveState): string | undefined {
  if (state === 'saved') {
    return 'success';
  }
  if (state === 'error') {
    return 'error';
  }
  return undefined;
}
