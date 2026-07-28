import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import manifest from './manifest';

/**
 * The manifest is the installability contract (PLAN §4.2.11, design system §9.2).
 *
 * Its values are **pinned** by `docs/PLAN-COMPATIBILITY.md`, which resolved the PLAN-versus-design-
 * system disagreement in favour of `Trips` / `#0958D9` / `#F8FAFC`. They are asserted literally
 * here rather than compared against `design-tokens.ts`: a token rename should not be able to
 * silently change the installed app's identity, and an OS reads these values once at install time
 * — a wrong colour survives on the user's home screen until they reinstall.
 */
describe('web app manifest', () => {
  const result = manifest();

  it('uses the names PLAN-COMPATIBILITY pins', () => {
    expect(result.name).toBe('Travel Planner');
    expect(result.short_name).toBe('Trips');
  });

  it('uses the pinned theme and background colours', () => {
    expect(result.theme_color).toBe('#0958D9');
    expect(result.background_color).toBe('#F8FAFC');
  });

  it('is installable as a standalone app from the root', () => {
    expect(result.display).toBe('standalone');
    expect(result.start_url).toBe('/');
  });

  /**
   * Chromium refuses to offer installation without a 192 px and a 512 px icon, and Android crops
   * a non-maskable icon into its adaptive shape. PLAN §4.2.11 lists all three.
   */
  it('ships the 192, 512, and maskable icons installability needs', () => {
    const icons = result.icons ?? [];
    const bySize = (sizes: string, purpose: string) =>
      icons.find((icon) => icon.sizes === sizes && icon.purpose === purpose);

    expect(bySize('192x192', 'any')).toMatchObject({
      src: '/icons/icon-192.png',
      type: 'image/png',
    });
    expect(bySize('512x512', 'any')).toMatchObject({
      src: '/icons/icon-512.png',
      type: 'image/png',
    });
    expect(bySize('512x512', 'maskable')).toMatchObject({
      src: '/icons/icon-maskable-512.png',
      type: 'image/png',
    });
  });

  it('serves every icon from an absolute, same-origin path', () => {
    for (const icon of result.icons ?? []) {
      expect(icon.src.startsWith('/icons/'), `icon ${icon.src}`).toBe(true);
    }
  });

  /**
   * A manifest that lists an icon which is not on disk makes the app silently uninstallable —
   * the browser rejects the whole install prompt and says nothing in the UI. The icons are
   * committed build inputs produced by `scripts/generate-pwa-icons.mjs`, so their absence is a
   * real possibility during a rebase, not a hypothetical.
   */
  it('has a file on disk behind every icon it lists', () => {
    const publicDir = join(import.meta.dirname, '..', '..', 'public');
    for (const icon of result.icons ?? []) {
      expect(existsSync(join(publicDir, icon.src)), `missing public${icon.src}`).toBe(true);
    }
  });
});
