'use client';

import { Alert, Button } from 'antd';
import { useTranslations } from 'next-intl';
import { ApiError } from '@/lib/api/api-error';
import { cn } from '@/lib/utils/cn';

export interface ErrorAlertProps {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}

/**
 * The one place an error becomes user-visible text (PLAN §4.2.6-K, §6.1).
 *
 * `ApiError.message` is an English developer string and is never rendered — the translated
 * message comes from `error.i18nKey`, so a Malay user sees Malay even for a backend fault. Two
 * details beyond that carry real weight:
 *
 * - **`request_id`** is shown when present. It is the only handle a user can quote that finds the
 *   exact failure in the logs, and asking for it after the fact never works.
 * - **`retry_after_seconds`** replaces the retry button with a wait, because `429`/`423` are the
 *   two states where retrying immediately is guaranteed to fail and makes the lockout worse.
 */
export function ErrorAlert({ error, onRetry, className }: ErrorAlertProps) {
  const t = useTranslations('common');
  const apiError = error instanceof ApiError ? error : null;
  const message = apiError ? t(apiError.i18nKey) : t('errors.internal_error');
  const retryAfter = apiError?.retryAfterSeconds ?? null;

  return (
    <Alert
      type="error"
      showIcon
      role="alert"
      className={cn('w-full', className)}
      message={t('states.error_title')}
      description={
        <div className="flex flex-col gap-2">
          <p className="m-0 text-body-sm">{message}</p>
          {retryAfter === null ? null : (
            <p className="m-0 text-caption text-foreground-muted">
              {t('states.retry_after', { seconds: retryAfter })}
            </p>
          )}
          {apiError?.requestId ? (
            <p className="m-0 text-caption text-foreground-subtle">
              {t('states.request_reference', { requestId: apiError.requestId })}
            </p>
          ) : null}
        </div>
      }
      action={
        onRetry && retryAfter === null ? (
          <Button size="small" onClick={onRetry}>
            {t('actions.retry')}
          </Button>
        ) : null
      }
    />
  );
}
