'use client';

import { useEffect, useState } from 'react';

/**
 * Browser connectivity, for the §9.3 baseline: "detection, honest messaging, local unsent chat
 * drafts, and blocking all server-required actions". Offline **caching** is ADR 005 / task 13;
 * this hook only reports.
 *
 * Starts optimistic rather than reading `navigator.onLine` during render: the server has no
 * `navigator`, and an initial value that differs between server and client is a hydration
 * mismatch. The effect corrects it on the first client tick.
 *
 * `navigator.onLine` only proves a network interface exists, not that the API is reachable — a
 * captive portal reports `true`. That is why an offline banner is advisory and request failures
 * are still handled on their own.
 */
export function useOnlineStatus(): boolean {
  const [isOnline, setIsOnline] = useState(true);

  useEffect(() => {
    const sync = () => setIsOnline(window.navigator.onLine);
    sync();
    window.addEventListener('online', sync);
    window.addEventListener('offline', sync);
    return () => {
      window.removeEventListener('online', sync);
      window.removeEventListener('offline', sync);
    };
  }, []);

  return isOnline;
}
