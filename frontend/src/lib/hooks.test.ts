import { act, renderHook } from '@testing-library/react';
import {
  IS_MAC,
  useBackToClose,
  useMediaQuery,
  useNow,
  useShortcuts,
  type Shortcut,
} from './hooks';

function press(key: string, init: KeyboardEventInit = {}, target: EventTarget = window) {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
  act(() => {
    target.dispatchEvent(event);
  });
  return event;
}

const modKey: KeyboardEventInit = IS_MAC ? { metaKey: true } : { ctrlKey: true };
const otherModKey: KeyboardEventInit = IS_MAC ? { ctrlKey: true } : { metaKey: true };

describe('useNow', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-25T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts at the current time', () => {
    const { result } = renderHook(() => useNow());

    expect(result.current).toBe(Date.parse('2026-11-25T12:00:00Z'));
  });

  it('ticks every second by default', () => {
    const { result } = renderHook(() => useNow());

    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(result.current).toBe(Date.parse('2026-11-25T12:00:01Z'));
  });

  it('does not tick before the interval ends', () => {
    const { result } = renderHook(() => useNow(5000));

    act(() => {
      vi.advanceTimersByTime(4999);
    });

    expect(result.current).toBe(Date.parse('2026-11-25T12:00:00Z'));
  });

  it('stops ticking when unmounted', () => {
    const { unmount } = renderHook(() => useNow());

    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});

describe('useShortcuts', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  function setup(shortcuts: Shortcut[], enabled?: boolean) {
    return renderHook(({ list, on }) => useShortcuts(list, on), {
      initialProps: { list: shortcuts, on: enabled },
    });
  }

  it('runs a single key shortcut and prevents the default action', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }]);

    const event = press('t');

    expect(run).toHaveBeenCalledTimes(1);
    expect(event.defaultPrevented).toBe(true);
  });

  it('matches a single key whatever its case', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }]);

    press('T');

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('does not run a single key shortcut when a modifier is held', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }]);

    press('t', { ctrlKey: true });
    press('t', { metaKey: true });
    press('t', { altKey: true });

    expect(run).not.toHaveBeenCalled();
  });

  it('runs mod+k with the platform modifier', () => {
    const run = vi.fn();
    setup([{ keys: 'mod+k', run }]);

    press('k', modKey);

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('does not run mod+k with the other platform modifier or without one', () => {
    const run = vi.fn();
    setup([{ keys: 'mod+k', run }]);

    press('k', otherModKey);
    press('k');

    expect(run).not.toHaveBeenCalled();
  });

  it('ignores events already handled or repeated', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }]);
    const handled = new KeyboardEvent('keydown', { key: 't', cancelable: true });
    handled.preventDefault();

    act(() => {
      window.dispatchEvent(handled);
    });
    press('t', { repeat: true });

    expect(run).not.toHaveBeenCalled();
  });

  it('runs a sequence pressed within a second', () => {
    const run = vi.fn();
    setup([{ keys: 'g f', run }]);

    press('g');
    act(() => {
      vi.advanceTimersByTime(999);
    });
    press('f');

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('does not run a sequence when the second key comes after a second', () => {
    const run = vi.fn();
    setup([{ keys: 'g f', run }]);

    press('g');
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    press('f');

    expect(run).not.toHaveBeenCalled();
  });

  it('does not run a sequence when its second key comes alone or after another key', () => {
    const run = vi.fn();
    setup([{ keys: 'g f', run }]);

    press('f');
    press('x');
    press('f');

    expect(run).not.toHaveBeenCalled();
  });

  it('forgets the first key of a sequence once it has run', () => {
    const run = vi.fn();
    setup([{ keys: 'g f', run }]);

    press('g');
    press('f');
    press('f');

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('ignores shortcuts while typing in a field', () => {
    const run = vi.fn();
    setup([
      { keys: 't', run },
      { keys: 'mod+k', run },
    ]);
    const input = document.body.appendChild(document.createElement('input'));
    const textarea = document.body.appendChild(document.createElement('textarea'));

    press('t', {}, input);
    press('k', modKey, textarea);

    expect(run).not.toHaveBeenCalled();
  });

  it('ignores shortcuts in an editable element', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }]);
    const editor = document.body.appendChild(document.createElement('div'));
    Object.defineProperty(editor, 'isContentEditable', { value: true });

    press('t', {}, editor);

    expect(run).not.toHaveBeenCalled();
  });

  it('does not start a sequence from a key typed in a field', () => {
    const run = vi.fn();
    setup([{ keys: 'g f', run }]);
    const input = document.body.appendChild(document.createElement('input'));

    press('g', {}, input);
    press('f');

    expect(run).not.toHaveBeenCalled();
  });

  it('runs a shortcut in a field when it allows it', () => {
    const run = vi.fn();
    setup([{ keys: 'mod+k', run, allowInFields: true }]);
    const select = document.body.appendChild(document.createElement('select'));

    press('k', modKey, select);

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('runs a shortcut fired on a non element target', () => {
    const run = vi.fn();
    setup([{ keys: '?', run }]);

    press('?', {}, document);

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('does nothing while disabled', () => {
    const run = vi.fn();
    setup([{ keys: 't', run }], false);

    press('t');

    expect(run).not.toHaveBeenCalled();
  });

  it('uses the latest shortcuts without listening again', () => {
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = setup([{ keys: 't', run: first }]);

    rerender({ list: [{ keys: 't', run: second }], on: undefined });
    press('t');

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });

  it('stops listening when unmounted', () => {
    const run = vi.fn();
    const { unmount } = setup([{ keys: 't', run }]);

    unmount();
    press('t');

    expect(run).not.toHaveBeenCalled();
  });
});

describe('IS_MAC', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('is true on a Mac platform, where mod means the command key', async () => {
    vi.stubGlobal('navigator', { platform: 'MacIntel', userAgent: '' });
    vi.resetModules();
    const hooks = await import('./hooks');
    const run = vi.fn();
    renderHook(() => hooks.useShortcuts([{ keys: 'mod+k', run }]));

    press('k', { ctrlKey: true });
    press('k', { metaKey: true });

    expect(hooks.IS_MAC).toBe(true);
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('is false elsewhere, where mod means the control key', async () => {
    vi.stubGlobal('navigator', { platform: 'Linux x86_64', userAgent: '' });
    vi.resetModules();
    const hooks = await import('./hooks');
    const run = vi.fn();
    renderHook(() => hooks.useShortcuts([{ keys: 'mod+k', run }]));

    press('k', { metaKey: true });
    press('k', { ctrlKey: true });

    expect(hooks.IS_MAC).toBe(false);
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('falls back to the user agent when there is no platform', async () => {
    vi.stubGlobal('navigator', { platform: '', userAgent: 'Mozilla/5.0 (iPhone)' });
    vi.resetModules();

    const hooks = await import('./hooks');

    expect(hooks.IS_MAC).toBe(true);
  });
});

describe('useBackToClose', () => {
  let pushState: ReturnType<typeof vi.spyOn>;
  let back: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    window.history.replaceState(null, '');
    pushState = vi.spyOn(window.history, 'pushState');
    back = vi.spyOn(window.history, 'back').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.history.replaceState(null, '');
  });

  function setup(open: boolean, close = vi.fn()) {
    const hook = renderHook(({ isOpen }) => useBackToClose(isOpen, close), {
      initialProps: { isOpen: open },
    });
    return { ...hook, close };
  }

  it('does nothing while closed', () => {
    setup(false);

    expect(pushState).not.toHaveBeenCalled();
  });

  it('pushes an overlay history entry when opened', () => {
    setup(true);

    expect(pushState).toHaveBeenCalledTimes(1);
    expect(window.history.state).toEqual({ riarideOverlay: expect.any(Number) as number });
  });

  it('closes the overlay on the back gesture without going back again', () => {
    const { close, rerender } = setup(true);

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    rerender({ isOpen: false });

    expect(close).toHaveBeenCalledTimes(1);
    expect(back).not.toHaveBeenCalled();
  });

  it('removes its history entry when closed by hand', () => {
    const { rerender } = setup(true);

    rerender({ isOpen: false });

    expect(back).toHaveBeenCalledTimes(1);
  });

  it('does not go back when the current entry is not its own', () => {
    const { rerender } = setup(true);
    window.history.replaceState({ other: true }, '');

    rerender({ isOpen: false });

    expect(back).not.toHaveBeenCalled();
  });

  it('stops listening for the back gesture once closed', () => {
    const { close, rerender } = setup(true);
    rerender({ isOpen: false });

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'));
    });

    expect(close).not.toHaveBeenCalled();
  });
});

describe('useMediaQuery', () => {
  const original = window.matchMedia;

  afterEach(() => {
    Object.defineProperty(window, 'matchMedia', { writable: true, value: original });
  });

  function stubMatchMedia(initial: boolean) {
    let matches = initial;
    const listeners = new Set<() => void>();
    const matchMedia = vi.fn((query: string) => ({
      get matches() {
        return matches;
      },
      media: query,
      addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
      removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
    }));
    Object.defineProperty(window, 'matchMedia', { writable: true, value: matchMedia });
    return {
      matchMedia,
      listeners,
      change(next: boolean) {
        matches = next;
        listeners.forEach((listener) => listener());
      },
    };
  }

  it('reports whether the query matches', () => {
    const media = stubMatchMedia(true);

    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'));

    expect(result.current).toBe(true);
    expect(media.matchMedia).toHaveBeenCalledWith('(min-width: 768px)');
  });

  it('updates when the media changes', () => {
    const media = stubMatchMedia(false);
    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'));

    act(() => {
      media.change(true);
    });

    expect(result.current).toBe(true);
  });

  it('stops listening when unmounted', () => {
    const media = stubMatchMedia(false);
    const { unmount } = renderHook(() => useMediaQuery('(min-width: 768px)'));

    unmount();

    expect(media.listeners.size).toBe(0);
  });

  it('is false when the browser has no matchMedia', () => {
    Object.defineProperty(window, 'matchMedia', { writable: true, value: undefined });

    const { result } = renderHook(() => useMediaQuery('(min-width: 768px)'));

    expect(result.current).toBe(false);
  });
});
