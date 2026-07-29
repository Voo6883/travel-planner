/**
 * Raw palette and scale values.
 *
 * `docs/UI-UX-DESIGN-SYSTEM.md` §3.1: **raw hex may appear only in this file.** Everything else
 * — components, Tailwind utilities, the Ant Design theme — consumes semantic role names. That
 * rule is what makes dark mode a variable swap rather than a component rewrite.
 *
 * Task 11 completes the set task 03 started: the typography scale (§3.2), spacing grid (§3.3),
 * radius/shadow (§3.4), and motion (§3.6) now live here too, so Tailwind and Ant read one source.
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
    // Leading zero is required: Prettier normalises `.56` to `0.56` in globals.css, and
    // design-tokens.test.ts compares the two spellings literally.
    scrim: 'rgba(15,23,42,0.56)',
    'action-primary-fill': '#0958D9',
    'action-primary-fill-hover': '#003EB3',
    'action-primary-fill-active': '#002C8C',
    'action-primary-text': '#0958D9',
    'action-primary-text-hover': '#003EB3',
    'focus-ring': '#1677FF',
    'selection-surface': '#E6F4FF',
    'selection-border': '#BAE0FF',
    'selection-text': '#0958D9',
    'ai-accent-text': '#006D75',
    'ai-accent-surface': '#E6FFFB',
    'chat-user-surface': '#0958D9',
    'chat-user-text': '#FFFFFF',
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
    scrim: 'rgba(0,0,0,0.68)',
    'action-primary-fill': '#0958D9',
    'action-primary-fill-hover': '#003EB3',
    'action-primary-fill-active': '#002C8C',
    'action-primary-text': '#69B1FF',
    'action-primary-text-hover': '#91CAFF',
    'focus-ring': '#69B1FF',
    'selection-surface': '#082F49',
    'selection-border': '#4096FF',
    'selection-text': '#BAE0FF',
    'ai-accent-text': '#5EEAD4',
    'ai-accent-surface': '#042F2E',
    'chat-user-surface': '#0958D9',
    'chat-user-text': '#FFFFFF',
  },
} as const;

export type SemanticTokenName = keyof (typeof semanticTokens)['light'];

/**
 * Typography scale (§3.2). `[fontSize, { lineHeight, fontWeight }]` is Tailwind's own tuple shape,
 * so `text-h1` carries size, leading, and weight together and a heading cannot drift by half a
 * step. `h1-mobile` is a separate step rather than a breakpoint variant because §3.2 gives it its
 * own size.
 */
export type TypeStep = [fontSize: string, config: { lineHeight: string; fontWeight: string }];

export const typographyScale: Record<string, TypeStep> = {
  display: ['40px', { lineHeight: '48px', fontWeight: '700' }],
  h1: ['30px', { lineHeight: '38px', fontWeight: '700' }],
  'h1-mobile': ['24px', { lineHeight: '32px', fontWeight: '700' }],
  h2: ['24px', { lineHeight: '32px', fontWeight: '600' }],
  h3: ['20px', { lineHeight: '28px', fontWeight: '600' }],
  title: ['16px', { lineHeight: '24px', fontWeight: '600' }],
  body: ['16px', { lineHeight: '24px', fontWeight: '400' }],
  'body-sm': ['14px', { lineHeight: '20px', fontWeight: '400' }],
  label: ['14px', { lineHeight: '20px', fontWeight: '600' }],
  caption: ['12px', { lineHeight: '16px', fontWeight: '500' }],
  metric: ['28px', { lineHeight: '34px', fontWeight: '700' }],
};

export const fontFamily =
  "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";

/** 4 px base grid (§3.3). Tailwind's defaults already match; these are the *named* steps. */
export const spacingScale = {
  1: '4px',
  2: '8px',
  3: '12px',
  4: '16px',
  5: '20px',
  6: '24px',
  8: '32px',
  10: '40px',
  12: '48px',
  16: '64px',
} as const;

export const radiusScale = {
  sm: '6px',
  md: '8px',
  lg: '12px',
  xl: '16px',
  full: '9999px',
} as const;

export const shadowScale = {
  sm: '0 1px 2px rgba(15,23,42,.06)',
  md: '0 8px 24px rgba(15,23,42,.10)',
  lg: '0 20px 48px rgba(15,23,42,.16)',
} as const;

/** §3.6. Durations and easings are tokens so "180 ms ease-out" is never retyped in a component. */
export const motionTokens = {
  duration: {
    color: '120ms',
    expand: '180ms',
    overlay: '220ms',
    page: '240ms',
    shimmer: '1500ms',
  },
  easing: {
    out: 'cubic-bezier(0, 0, 0.2, 1)',
    overlay: 'cubic-bezier(.2,.8,.2,1)',
  },
} as const;

/**
 * Minimum interactive size (§10.1 / §6.1–6.2). 44 px is a WCAG 2.2 AA target size, not a visual
 * preference, so it is a token rather than a per-component decision.
 */
export const controlHeight = {
  default: 44,
  large: 48,
} as const;
