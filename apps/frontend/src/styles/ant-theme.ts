import type { ThemeConfig } from 'antd';
import { palette, semanticTokens } from './design-tokens';

/**
 * Minimal, deliberately non-final bridge from design tokens to Ant Design.
 *
 * Task 03 maps only what the shell needs so Ant components do not arrive with default
 * Ant blue. The complete component-level theme (buttons, inputs, tables, per-component
 * overrides) belongs to tasks/11-frontend-platform-auth-ui.md against the full
 * docs/UI-UX-DESIGN-SYSTEM.md spec.
 *
 * Tailwind overrides Ant where they collide (PLAN §4.2.9).
 */
export const antTheme: ThemeConfig = {
  token: {
    colorPrimary: semanticTokens.light['action-primary-fill'],
    colorLink: semanticTokens.light['action-primary-text'],
    colorSuccess: semanticTokens.light.success,
    colorWarning: semanticTokens.light.warning,
    colorError: semanticTokens.light.destructive,
    colorInfo: semanticTokens.light.info,
    colorBgBase: semanticTokens.light.surface,
    colorTextBase: semanticTokens.light.foreground,
    colorBorder: semanticTokens.light['border-subtle'],
    // Focus ring is owned by globals.css so it stays identical across Ant and non-Ant controls.
    colorPrimaryHover: palette.blue[700],
    colorPrimaryActive: palette.blue[800],
    fontFamily:
      "Inter, ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  },
};
