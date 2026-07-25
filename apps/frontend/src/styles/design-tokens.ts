/**
 * Raw palette values.
 *
 * `docs/UI-UX-DESIGN-SYSTEM.md` §3.1: **raw hex may appear only in this file.** Everything else
 * — components, Tailwind utilities, the Ant Design theme — consumes semantic role names. That
 * rule is what makes dark mode a variable swap rather than a component rewrite.
 *
 * This is the Task 03 subset: the palette and the role tokens needed by the shell. The full
 * token set (typography scale, spacing, radius, shadow, motion) lands with
 * tasks/11-frontend-platform-auth-ui.md.
 */

export const palette = {
  blue: {
    50: '#E6F4FF',
    100: '#BAE0FF',
    200: '#91CAFF',
    300: '#69B1FF',
    400: '#4096FF',
    500: '#1677FF',
    600: '#0958D9',
    700: '#003EB3',
    800: '#002C8C',
    900: '#001D66',
  },
  teal: {
    50: '#E6FFFB',
    100: '#B5F5EC',
    500: '#13C2C2',
    600: '#08979C',
    700: '#006D75',
  },
  amber: {
    50: '#FFF7E6',
    100: '#FFE7BA',
    500: '#FA8C16',
    700: '#AD4E00',
  },
} as const;

/** Semantic tokens. Light and dark differ only in value — never in name. */
export const semanticTokens = {
  light: {
    canvas: '#F8FAFC',
    surface: '#FFFFFF',
    'surface-subtle': '#F1F5F9',
    'surface-elevated': '#FFFFFF',
    foreground: '#0F172A',
    'foreground-muted': '#475569',
    'foreground-subtle': '#5F6F84',
    border: '#64748B',
    'border-subtle': '#E2E8F0',
    success: '#15803D',
    'success-surface': '#F0FDF4',
    warning: '#AD4E00',
    'warning-surface': '#FFF7E6',
    destructive: '#B91C1C',
    'destructive-surface': '#FEF2F2',
    info: '#0369A1',
    'info-surface': '#F0F9FF',
    'action-primary-fill': '#0958D9',
    'action-primary-fill-hover': '#003EB3',
    'action-primary-fill-active': '#002C8C',
    'action-primary-text': '#0958D9',
    'focus-ring': '#1677FF',
    'selection-surface': '#E6F4FF',
    'selection-border': '#BAE0FF',
    'selection-text': '#0958D9',
    'ai-accent-text': '#006D75',
    'ai-accent-surface': '#E6FFFB',
  },
  dark: {
    canvas: '#0B1220',
    surface: '#111827',
    'surface-subtle': '#172033',
    'surface-elevated': '#1E293B',
    foreground: '#F8FAFC',
    'foreground-muted': '#CBD5E1',
    'foreground-subtle': '#94A3B8',
    border: '#94A3B8',
    'border-subtle': '#334155',
    success: '#4ADE80',
    'success-surface': '#052E16',
    warning: '#FDBA74',
    'warning-surface': '#431407',
    destructive: '#F87171',
    'destructive-surface': '#450A0A',
    info: '#7DD3FC',
    'info-surface': '#082F49',
    'action-primary-fill': '#0958D9',
    'action-primary-fill-hover': '#003EB3',
    'action-primary-fill-active': '#002C8C',
    'action-primary-text': '#69B1FF',
    'focus-ring': '#69B1FF',
    'selection-surface': '#082F49',
    'selection-border': '#4096FF',
    'selection-text': '#BAE0FF',
    'ai-accent-text': '#5EEAD4',
    'ai-accent-surface': '#042F2E',
  },
} as const;

export type SemanticTokenName = keyof (typeof semanticTokens)['light'];
