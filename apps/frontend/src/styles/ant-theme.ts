import { theme, type ThemeConfig } from 'antd';
import { controlHeight, fontFamily, radiusScale, semanticTokens, shadowScale } from './design-tokens';

export type ThemeMode = 'light' | 'dark';

/**
 * The `docs/UI-UX-DESIGN-SYSTEM.md` §12.2 token mapping, in full.
 *
 * Two things in that table are easy to get wrong and are the reason this is a function rather
 * than a constant:
 *
 * 1. **Global `colorPrimary` maps to `action-primary-text`, not to the button fill.** In dark mode
 *    those diverge — text lightens to `#69B1FF` for contrast on a dark canvas while the filled
 *    button stays `#0958D9` so white label text keeps 4.5:1. Feeding the fill colour into
 *    `colorPrimary` would make every link and active tab fail contrast in dark mode.
 * 2. Filled buttons therefore need component-level overrides; the global token cannot express
 *    "light text, dark fill".
 *
 * Tailwind overrides Ant where they collide (PLAN §4.2.9); this exists so Ant's *internals* —
 * ripples, hover states, disabled shades — stay on the same palette as the utilities.
 */
export function buildAntTheme(mode: ThemeMode): ThemeConfig {
  const tokens = semanticTokens[mode];

  return {
    algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: tokens['action-primary-text'],
      colorPrimaryHover: tokens['action-primary-text-hover'],
      colorLink: tokens['action-primary-text'],
      colorLinkHover: tokens['action-primary-text-hover'],
      colorSuccess: tokens.success,
      colorWarning: tokens.warning,
      colorError: tokens.destructive,
      colorInfo: tokens.info,
      colorText: tokens.foreground,
      colorTextSecondary: tokens['foreground-muted'],
      colorTextTertiary: tokens['foreground-subtle'],
      colorBgBase: tokens.canvas,
      colorBgContainer: tokens.surface,
      colorBgElevated: tokens['surface-elevated'],
      colorBorder: tokens.border,
      colorBorderSecondary: tokens['border-subtle'],
      borderRadius: Number.parseInt(radiusScale.md, 10),
      borderRadiusLG: Number.parseInt(radiusScale.lg, 10),
      fontFamily,
      fontSize: 16,
      // 44 px is the WCAG 2.2 AA target size (§10.1), applied at every breakpoint (§6.2).
      controlHeight: controlHeight.default,
      controlHeightLG: controlHeight.large,
      boxShadow: shadowScale.sm,
      boxShadowSecondary: shadowScale.md,
    },
    components: {
      Button: {
        colorPrimary: tokens['action-primary-fill'],
        colorPrimaryHover: tokens['action-primary-fill-hover'],
        colorPrimaryActive: tokens['action-primary-fill-active'],
        // §12.2 pins white regardless of mode — the fill is dark enough in both.
        primaryColor: '#FFFFFF',
        fontWeight: 500,
      },
      Card: {
        borderRadiusLG: Number.parseInt(radiusScale.lg, 10),
        paddingLG: 24,
      },
      Modal: {
        borderRadiusLG: Number.parseInt(radiusScale.xl, 10),
      },
      Drawer: {
        borderRadiusLG: Number.parseInt(radiusScale.xl, 10),
      },
      Table: {
        headerBg: tokens['surface-subtle'],
        rowHoverBg: tokens['surface-subtle'],
        cellPaddingBlock: 14,
      },
      Alert: {
        borderRadiusLG: Number.parseInt(radiusScale.lg, 10),
      },
    },
  };
}
