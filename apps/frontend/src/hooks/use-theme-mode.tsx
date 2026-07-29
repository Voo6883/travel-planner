'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { ThemeMode } from '@/styles/ant-theme';

/** §13: `system` is the default, with `light` and `dark` as explicit overrides. */
export type ThemePreference = 'system' | 'light' | 'dark';

export const THEME_STORAGE_KEY = 'tp-theme';

export interface ThemeModeContextValue {
  /** What the user chose. */
  preference: ThemePreference;
  /** What that resolves to right now — what Ant and Tailwind actually render. */
  mode: ThemeMode;
  setPreference: (preference: ThemePreference) => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null);

/**
 * The **one** shared mode state §12.2 requires: Tailwind reads the `light`/`dark` class on
 * `<html>`, Ant reads `mode` through `ConfigProvider`, and both come from here.
 *
 * The class is also written by a blocking script in `app/layout.tsx` before first paint, so the
 * effect below re-applies rather than introduces it — §13 forbids flashing the wrong mode during
 * hydration, and a React effect always runs too late to prevent that on its own.
 */
export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>('system');
  const [systemMode, setSystemMode] = useState<ThemeMode>('light');

  useEffect(() => {
    setPreferenceState(readStoredPreference());
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const sync = () => setSystemMode(query.matches ? 'dark' : 'light');
    sync();
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);

  const mode: ThemeMode = preference === 'system' ? systemMode : preference;

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', mode === 'dark');
    root.classList.toggle('light', mode === 'light');
  }, [mode]);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    window.localStorage.setItem(THEME_STORAGE_KEY, next);
  }, []);

  const value = useMemo(() => ({ preference, mode, setPreference }), [preference, mode, setPreference]);

  return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>;
}

export function useThemeMode(): ThemeModeContextValue {
  const value = useContext(ThemeModeContext);
  if (value === null) {
    throw new Error('useThemeMode must be used inside ThemeModeProvider');
  }
  return value;
}

function readStoredPreference(): ThemePreference {
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
}
