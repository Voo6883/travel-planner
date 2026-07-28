'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { OfflineBanner } from '@/components/ui/offline-banner';
import { useCurrentUser } from '@/features/auth';
import { cn } from '@/lib/utils/cn';
import { AccountMenu } from './account-menu';
import { ADMIN_NAV_ITEM, PLANNER_NAV_ITEMS, type NavItem } from './app-nav';

export interface AppShellProps {
  children: ReactNode;
  /** Rendered next to the brand as an "Admin" context label (§8.10). */
  contextLabel?: string;
}

/**
 * The authenticated shell (§4.3, §5.1).
 *
 * Two presentations of one navigation list: a 240 px sidebar from `lg` up, and a 64 px bottom bar
 * below `md`. §4.3 asks for exactly that, and the plan's collapsible chat panel and trip stepper
 * are deliberately absent — they belong to the trip layout in tasks 21 and 31, not to every
 * authenticated page.
 *
 * The bottom bar carries `pb-[env(safe-area-inset-bottom)]` through a utility class rather than
 * an arbitrary value; §4.4 requires the inset on full-screen PWA navigation, and without it the
 * last item sits under the home indicator on iOS.
 */
export function AppShell({ children, contextLabel }: AppShellProps) {
  const t = useTranslations('common');
  const { data: user } = useCurrentUser();
  const isAdmin = user?.roles.includes('ADMIN') ?? false;

  return (
    <div className="flex min-h-dvh flex-col">
      <OfflineBanner />

      <header className="sticky top-0 z-10 flex h-14 items-center justify-between gap-3 border-b border-border-subtle bg-surface px-4 md:h-16 md:px-6">
        <div className="flex items-center gap-3">
          <Link href="/trips" className="text-title text-foreground no-underline">
            <span aria-hidden="true" className="mr-2 text-action-primary-text">
              ◈
            </span>
            {t('app_name')}
          </Link>
          {contextLabel ? (
            <span className="rounded-sm bg-selection-surface px-2 py-1 text-caption text-selection-text">
              {contextLabel}
            </span>
          ) : null}
        </div>
        <AccountMenu />
      </header>

      <div className="flex flex-1">
        <SideNav isAdmin={isAdmin} />
        <main id="main-content" className="min-w-0 flex-1 pb-20 md:pb-0">
          {children}
        </main>
      </div>

      <BottomNav />
    </div>
  );
}

function SideNav({ isAdmin }: { isAdmin: boolean }) {
  const t = useTranslations('common');
  const pathname = usePathname();

  return (
    <nav
      aria-label={t('nav.primary_label')}
      className="hidden w-60 shrink-0 border-r border-border-subtle bg-surface p-4 lg:block"
    >
      <ul className="m-0 flex list-none flex-col gap-1 p-0">
        {PLANNER_NAV_ITEMS.map((item) => (
          <li key={item.href}>
            <NavLink item={item} label={t(`nav.${item.labelKey}`)} pathname={pathname} />
          </li>
        ))}
      </ul>

      {/* §5.1: admin is shown only to authorised users and is visually separated. */}
      {isAdmin ? (
        <div className="mt-6 border-t border-border-subtle pt-4">
          <NavLink item={ADMIN_NAV_ITEM} label={t('nav.admin')} pathname={pathname} />
        </div>
      ) : null}
    </nav>
  );
}

function BottomNav() {
  const t = useTranslations('common');
  const pathname = usePathname();

  return (
    <nav
      aria-label={t('nav.primary_label')}
      className="fixed inset-x-0 bottom-0 z-10 flex border-t border-border-subtle bg-surface pb-safe md:hidden"
    >
      {PLANNER_NAV_ITEMS.map((item) => (
        <NavLink
          key={item.href}
          item={item}
          label={t(`nav.${item.labelKey}`)}
          pathname={pathname}
          className="flex-1 flex-col justify-center gap-1 text-caption"
        />
      ))}
    </nav>
  );
}

interface NavLinkProps {
  item: NavItem;
  label: string;
  pathname: string;
  className?: string;
}

function NavLink({ item, label, pathname, className }: NavLinkProps) {
  const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      // `aria-current` is what tells a screen-reader user where they are; the colour alone is not
      // information (§10.1). The icon is decorative — the text label carries the meaning (§3.5).
      aria-current={isActive ? 'page' : undefined}
      className={cn(
        'flex min-h-control items-center gap-2 rounded-md px-3 text-body-sm no-underline',
        isActive
          ? 'bg-selection-surface font-semibold text-selection-text'
          : 'text-foreground-muted hover:bg-surface-subtle',
        className,
      )}
    >
      <Icon aria-hidden="true" />
      {label}
    </Link>
  );
}
