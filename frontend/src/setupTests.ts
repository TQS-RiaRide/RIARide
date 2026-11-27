import '@testing-library/jest-dom/vitest';

// jsdom has no matchMedia; components and libraries (sonner, vaul) ask for it. Nothing matches.
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
}

// jsdom does not scroll; the router restores scroll positions on navigation.
window.scrollTo = () => undefined;
