import { CompassOutlined, SafetyCertificateOutlined, SettingOutlined } from '@ant-design/icons';
import type { ComponentType } from 'react';

export interface NavItem {
  href: string;
  /** Key in the `common.nav` namespace — never a literal label (§11.3). */
  labelKey: string;
  icon: ComponentType;
}

/**
 * Planner navigation, in the order §5.1 fixes: Trips, then Settings.
 *
 * Two omissions, both deliberate. "Explore" is admitted by §5.1 "only if a future use case is
 * approved", and there is none. "Help" has no route and no owning task — a nav entry that 404s is
 * a worse failure than an absent one, so it is left out and recorded as a follow-up rather than
 * shipped broken or filled with invented support copy.
 *
 * Admin is **not** here either. §5.1 requires it to be visually separated from planner navigation
 * and shown only to authorised users, so the shell renders it separately.
 *
 * Data, not JSX, so the sidebar and the mobile bottom bar cannot drift out of sync — one list,
 * two presentations.
 */
export const PLANNER_NAV_ITEMS: readonly NavItem[] = [
  { href: '/trips', labelKey: 'trips', icon: CompassOutlined },
  { href: '/settings', labelKey: 'settings', icon: SettingOutlined },
];

/** Rendered separately, and only for `ADMIN` — see above. */
export const ADMIN_NAV_ITEM: NavItem = {
  href: '/admin',
  labelKey: 'admin',
  icon: SafetyCertificateOutlined,
};
