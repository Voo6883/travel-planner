/** Route to one account's admin detail screen. One definition, so a rename cannot half-apply. */
export function adminUserRoute(userId: string): string {
  return `/admin/users/${userId}`;
}

/**
 * An ISO-8601 instant rendered as its calendar date in UTC.
 *
 * Deliberately not locale-formatted. These components are rendered on the server and hydrated in
 * the browser, and a formatter that reads the local time zone produces different text on each
 * side — a hydration mismatch that React reports as an error and that shows up only on machines
 * whose zone differs from the server's. An administrative table wants an unambiguous, sortable
 * date far more than a localised one, and `2026-07-28` reads the same in `en` and `ms`.
 *
 * @returns the `YYYY-MM-DD` portion, or the input unchanged when it is not a parseable instant —
 *     a malformed timestamp should not blank a table cell
 */
export function formatIsoDate(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toISOString().slice(0, 10);
}
