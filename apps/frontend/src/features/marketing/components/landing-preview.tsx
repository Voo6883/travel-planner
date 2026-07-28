import { BookOutlined } from '@ant-design/icons';
import { getTranslations } from 'next-intl/server';

/** Composed chat preview for the marketing hero (§8.1 — product preview, not a carousel). */
export async function LandingPreview() {
  const t = await getTranslations('marketing');

  return (
    <div
      aria-hidden="true"
      className="w-full overflow-hidden rounded-xl border border-border-subtle bg-surface shadow-md"
    >
      <div className="border-b border-border-subtle bg-surface-subtle px-4 py-3">
        <p className="m-0 text-label text-foreground">{t('preview_trip_label')}</p>
      </div>

      <div className="flex flex-col gap-4 p-4 sm:p-6">
        <div className="flex justify-end">
          <div className="max-w-[85%] rounded-lg rounded-br-sm bg-chat-user-surface px-4 py-3">
            <p className="m-0 text-body-sm text-chat-user-text">{t('preview_user_message')}</p>
          </div>
        </div>

        <div className="flex justify-start">
          <div className="max-w-[90%] rounded-lg rounded-bl-sm border border-border-subtle bg-surface px-4 py-3">
            <p className="m-0 text-body-sm text-foreground">{t('preview_assistant_message')}</p>
            <p className="mb-0 mt-3 flex items-center gap-2 text-caption text-ai-accent-text">
              <BookOutlined aria-hidden className="text-sm" />
              {t('preview_source_label')}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
