'use client';

import { GithubOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { useTranslations } from 'next-intl';
import { githubOAuthStartUrl, type GithubOAuthMode } from '@/lib/api/auth-api';
import type { AuthMode } from '../types';

export interface GithubSignInButtonProps {
  mode: AuthMode;
  /** `link` attaches GitHub to the signed-in account instead of signing anyone in (UC-A09). */
  flow?: GithubOAuthMode;
  label?: string;
}

/**
 * GitHub OAuth start (UC-A03, UC-A06).
 *
 * **A full-page navigation, never a `fetch`.** The endpoint answers `302` to GitHub's authorize
 * page and sets an `httpOnly` `tp_oauth_state` cookie that the callback checks. An XHR would
 * follow that redirect invisibly, hit github.com under this origin's CORS rules, and fail — and
 * even if it somehow succeeded the user would never see the consent screen, which is the entire
 * point of the flow.
 *
 * `window.location.assign` rather than `router.push`: the destination is outside the Next.js
 * router's world, and the client-side router would try to resolve it as an app route.
 *
 * The success and failure landings both come back as browser navigations too — `/trips` or
 * `/trips?linked=1` on success, `/sign-in?error=<code>` on failure — so no callback handler is
 * needed on this side beyond reading those query parameters.
 */
export function GithubSignInButton({ mode, flow = 'sign_in', label }: GithubSignInButtonProps) {
  const t = useTranslations('auth');
  const fallback = mode === 'sign_up' ? t('sign_up_with_github') : t('continue_with_github');

  return (
    <Button
      block
      size="large"
      // Decorative: the label already says GitHub, and an Ant icon otherwise announces its own
      // name as part of the button's accessible name.
      icon={<GithubOutlined aria-hidden="true" />}
      onClick={() => window.location.assign(githubOAuthStartUrl(flow))}
    >
      {label ?? fallback}
    </Button>
  );
}
