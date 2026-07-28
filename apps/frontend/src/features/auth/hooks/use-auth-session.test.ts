import { describe, expect, it } from 'vitest';
import { PLANNER_HOME_ROUTE, safeRedirect } from './use-auth-session';

/**
 * `?redirect=` exists so a guard can return the user where they were aiming. It is also, if left
 * unchecked, an open redirect: a phishing mail links to our own sign-in page, the user completes a
 * genuine sign-in, and lands on an attacker's site with our domain in their history and their
 * trust already spent.
 */
describe('safeRedirect', () => {
  it('keeps a same-origin path', () => {
    expect(safeRedirect('/trips/abc/research')).toBe('/trips/abc/research');
  });

  it.each([
    'https://evil.example/steal',
    'http://evil.example',
    // Protocol-relative: browsers read this as absolute, and it is the form the check most often
    // forgets.
    '//evil.example/steal',
    'javascript:alert(1)',
    'trips',
    '',
  ])('refuses %s and falls back to the planner', (target) => {
    expect(safeRedirect(target)).toBe(PLANNER_HOME_ROUTE);
  });

  it('falls back when the parameter is absent', () => {
    expect(safeRedirect(null)).toBe(PLANNER_HOME_ROUTE);
  });
});
