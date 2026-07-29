import type { Config } from 'tailwindcss';
import { fontFamily, motionTokens, radiusScale, shadowScale, typographyScale } from './src/styles/design-tokens';

/**
 * Tailwind maps only to semantic CSS variables — never to raw hex. A component writes
 * `bg-action-primary-fill`, and dark mode is handled by the variable, not by the class.
 * Raw palette values live solely in src/styles/design-tokens.ts.
 *
 * Non-color scales (type, radius, shadow, motion) are imported from the token module rather than
 * retyped, so `docs/UI-UX-DESIGN-SYSTEM.md` §3.2–§3.6 has exactly one representation in code.
 *
 * Tailwind v3 is used deliberately: PLAN §4.2.4 locks `tailwind.config.ts` at the app root as
 * the configuration surface, which is v3's model.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  // `class` rather than `media`: §13 requires system / light / dark as an explicit user choice,
  // and Tailwind and Ant must switch off one shared mode state.
  darkMode: 'class',
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
        scrim: 'var(--scrim)',
        'action-primary': {
          fill: 'var(--action-primary-fill)',
          'fill-hover': 'var(--action-primary-fill-hover)',
          'fill-active': 'var(--action-primary-fill-active)',
          text: 'var(--action-primary-text)',
          'text-hover': 'var(--action-primary-text-hover)',
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
        'chat-user': {
          surface: 'var(--chat-user-surface)',
          text: 'var(--chat-user-text)',
        },
      },
      fontFamily: {
        sans: [fontFamily],
      },
      fontSize: typographyScale,
      borderRadius: radiusScale,
      boxShadow: shadowScale,
      transitionDuration: {
        color: motionTokens.duration.color,
        expand: motionTokens.duration.expand,
        overlay: motionTokens.duration.overlay,
        page: motionTokens.duration.page,
      },
      transitionTimingFunction: {
        out: motionTokens.easing.out,
        overlay: motionTokens.easing.overlay,
      },
      minHeight: {
        // §6.1/§6.2: 44 px controls, 48 px large controls — a11y target size, not a look.
        control: '44px',
        'control-lg': '48px',
      },
      minWidth: {
        control: '44px',
      },
      padding: {
        // §4.4: full-screen PWA navigation must clear the iOS home indicator.
        safe: 'env(safe-area-inset-bottom)',
      },
      maxWidth: {
        // §8.2 authentication card; §6.2 long-form maximum. Named rather than arbitrary so the
        // two places that need them cannot drift apart.
        'auth-card': '420px',
        form: '720px',
      },
    },
  },
  plugins: [],
};

export default config;
