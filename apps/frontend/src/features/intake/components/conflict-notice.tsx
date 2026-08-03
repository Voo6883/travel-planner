'use client';

import { Alert } from 'antd';
import { useTranslations } from 'next-intl';

export interface ConflictNoticeProps {
  visible: boolean;
  currentVersion?: number | null;
}

/**
 * ADR 008's conflict UX is non-blocking but explicit: the form kept the user's draft and pulled in
 * fresh server state where it was safe to do so.
 */
export function ConflictNotice({ visible, currentVersion }: ConflictNoticeProps) {
  const t = useTranslations('trip_brief');

  if (!visible) {
    return null;
  }

  return (
    <Alert
      showIcon
      type="warning"
      role="status"
      message={t('conflict.notice')}
      description={currentVersion ? t('conflict.current_version', { version: currentVersion }) : undefined}
    />
  );
}
