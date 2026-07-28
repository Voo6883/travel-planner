'use client';

import { App } from 'antd';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/lib/query/query-keys';

/** The flag `GET /auth/oauth/github/callback` appends after a successful link (UC-A09). */
export const LINKED_PARAM = 'linked';

/**
 * Confirms a completed GitHub link after the OAuth round trip.
 *
 * The link succeeds during a **browser redirect**, so no promise on this side ever resolves —
 * `?linked=1` on the landing URL is the only evidence it happened, and without this the user
 * returns to the planner with no indication that anything worked.
 *
 * Two things follow from that:
 *
 * - `/auth/me` is invalidated, because `linked_providers` changed while this tab was away and the
 *   cached copy predates the round trip.
 * - The parameter is stripped with `router.replace`, so a bookmark or a refresh does not replay
 *   the confirmation for something that happened once.
 *
 * A toast rather than an alert: §6.5 puts "short confirmation that needs no action" on toasts,
 * and pairing both for one event is explicitly forbidden.
 */
export function ProviderLinkedNotice() {
  const t = useTranslations('auth');
  const { message } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const hasAnnounced = useRef(false);

  const isLinked = searchParams.get(LINKED_PARAM) === '1';

  useEffect(() => {
    if (!isLinked || hasAnnounced.current) {
      return;
    }
    hasAnnounced.current = true;
    void message.success(t('provider_linked_query_notice'));
    void queryClient.invalidateQueries({ queryKey: queryKeys.auth.all });
    router.replace(pathname);
  }, [isLinked, message, t, queryClient, router, pathname]);

  return null;
}
