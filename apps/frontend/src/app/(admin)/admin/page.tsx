import { redirect } from 'next/navigation';

/**
 * `/admin` has no content of its own.
 *
 * Account management is the whole of v1 administration (PLAN §4.0.6), so a landing page here would
 * be one link on an otherwise empty screen — an extra click between an administrator and the only
 * thing they came for. It redirects instead, and becomes a real page the first time a second admin
 * capability exists to choose between.
 *
 * The redirect happens inside the `(admin)` group, so `AuthGuard require="admin"` in the layout
 * still guards the destination: a non-admin is bounced from `/admin/users` exactly as they would
 * have been from here.
 */
export default function AdminPage() {
  redirect('/admin/users');
}
