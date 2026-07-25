import type { Config } from 'tailwindcss';

/**
 * Tailwind maps only to semantic CSS variables — never to raw hex. A component writes
 * `bg-action-primary-fill`, and dark mode is handled by the variable, not by the class.
 * Raw palette values live solely in src/styles/design-tokens.ts.
 *
 * Tailwind v3 is used deliberately: PLAN §4.2.4 locks `tailwind.config.ts` at the app root as
 * the configuration surface, which is v3's model.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: 'var(--canvas)',
        surface: {
          DEFAULT: 'var(--surface)',
          subtle: 'var(--surface-subtle)',
          elevated: 'var(--surface-elevated)',
        },
        foreground: {
          DEFAULT: 'var(--foreground)',
          muted: 'var(--foreground-muted)',
          subtle: 'var(--foreground-subtle)',
        },
        border: {
          DEFAULT: 'var(--border)',
          subtle: 'var(--border-subtle)',
        },
        success: {
          DEFAULT: 'var(--success)',
          surface: 'var(--success-surface)',
        },
        warning: {
          DEFAULT: 'var(--warning)',
          surface: 'var(--warning-surface)',
        },
        destructive: {
          DEFAULT: 'var(--destructive)',
          surface: 'var(--destructive-surface)',
        },
        info: {
          DEFAULT: 'var(--info)',
          surface: 'var(--info-surface)',
        },
        'action-primary': {
          fill: 'var(--action-primary-fill)',
          'fill-hover': 'var(--action-primary-fill-hover)',
          'fill-active': 'var(--action-primary-fill-active)',
          text: 'var(--action-primary-text)',
        },
        selection: {
          surface: 'var(--selection-surface)',
          border: 'var(--selection-border)',
          text: 'var(--selection-text)',
        },
        'ai-accent': {
          text: 'var(--ai-accent-text)',
          surface: 'var(--ai-accent-surface)',
        },
      },
    },
  },
  plugins: [],
};

export default config;
