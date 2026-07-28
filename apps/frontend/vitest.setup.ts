import '@testing-library/jest-dom/vitest';

/**
 * jsdom implements neither of these, and Ant Design uses both — `matchMedia` for responsive
 * breakpoints and `ResizeObserver` for overflow-aware components. Without them a component test
 * fails on an environment gap rather than on anything the component does.
 *
 * Deliberately inert: `matchMedia` always reports "no match", so tests render the base (mobile)
 * breakpoint unless they say otherwise, and `ResizeObserver` never fires.
 */
if (typeof window !== 'undefined') {
  if (!window.matchMedia) {
    window.matchMedia = (query: string): MediaQueryList =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList;
  }

  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
}
