'use client';

import { Divider } from 'antd';
import { useTranslations } from 'next-intl';
import { GithubSignInButton } from './github-sign-in-button';
import { GoogleSignInButton } from './google-sign-in-button';
import type { AuthProviderButtonsProps } from '../types';

/**
 * Providers first, then a divider, then local credentials — the order §8.2 specifies.
 *
 * Both providers appear on **both** pages. Splitting them ("Google on sign-in, GitHub on
 * sign-up") would be a guess about which account someone already has, and the endpoints behind
 * them treat sign-up and sign-in as the same operation anyway.
 */
export function AuthProviderButtons({ mode }: AuthProviderButtonsProps) {
  const t = useTranslations('auth');

  return (
    <div className="flex flex-col gap-3">
      <GoogleSignInButton mode={mode} />
      <GithubSignInButton mode={mode} />
      <Divider plain className="my-2 text-caption text-foreground-subtle">
        {t('or_divider')}
      </Divider>
    </div>
  );
}
