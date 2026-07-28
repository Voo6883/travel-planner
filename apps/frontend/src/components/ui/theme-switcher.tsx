'use client';

import { Segmented } from 'antd';
import { useTranslations } from 'next-intl';
import { useThemeMode, type ThemePreference } from '@/hooks/use-theme-mode';

/**
 * Appearance selector (§13): `system` by default, with `light` and `dark` as explicit overrides.
 *
 * A three-way control rather than a toggle, because "match system" is a distinct choice and a
 * two-state switch cannot express it — a user who picks light explicitly should stay light when
 * their OS flips at sunset.
 *
 * `Segmented` keeps all three options visible with a 44 px target and native keyboard support,
 * and the change applies instantly (§8.9: "locale and theme changes preview immediately").
 */
export function ThemeSwitcher() {
  const t = useTranslations('common');
  const { preference, setPreference } = useThemeMode();

  return (
    <Segmented<ThemePreference>
      block
      size="large"
      aria-label={t('theme.label')}
      value={preference}
      onChange={setPreference}
      options={[
        { value: 'system', label: t('theme.system') },
        { value: 'light', label: t('theme.light') },
        { value: 'dark', label: t('theme.dark') },
      ]}
    />
  );
}
