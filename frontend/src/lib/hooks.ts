import { useEffect, useRef, useState, useSyncExternalStore } from 'react';

/** The current time, ticking every `intervalMs` while mounted. */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName);
}

export const IS_MAC =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);

export interface Shortcut {
  /** "mod+k", "?", "t", or a sequence "g f" (press g, then f within a second). */
  keys: string;
  run: () => void;
  /** Also fire while typing in a field (only for combinations with mod). */
  allowInFields?: boolean;
}

const SEQUENCE_WINDOW_MS = 1000;

function matchesCombo(event: KeyboardEvent, combo: string): boolean {
  const parts = combo.toLowerCase().split('+');
  const key = parts.pop() ?? '';
  const wantsMod = parts.includes('mod');
  const mod = IS_MAC ? event.metaKey : event.ctrlKey;
  if (wantsMod !== mod) return false;
  if (!wantsMod && (event.metaKey || event.ctrlKey || event.altKey)) return false;
  return event.key.toLowerCase() === key;
}

/** Keyboard shortcuts for the whole page; ignored while typing unless allowed. */
export function useShortcuts(shortcuts: readonly Shortcut[], enabled = true): void {
  const latest = useRef(shortcuts);
  latest.current = shortcuts;

  useEffect(() => {
    if (!enabled) return undefined;
    let pending: { key: string; at: number } | null = null;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.repeat) return;
      const typing = isTyping(event.target);
      for (const shortcut of latest.current) {
        if (typing && !shortcut.allowInFields) continue;
        const steps = shortcut.keys.split(' ');
        if (steps.length === 2) {
          const [first, second] = steps;
          if (
            pending &&
            pending.key === first &&
            Date.now() - pending.at < SEQUENCE_WINDOW_MS &&
            matchesCombo(event, second)
          ) {
            event.preventDefault();
            pending = null;
            shortcut.run();
            return;
          }
          continue;
        }
        if (matchesCombo(event, shortcut.keys)) {
          event.preventDefault();
          pending = null;
          shortcut.run();
          return;
        }
      }
      if (!typing && !event.metaKey && !event.ctrlKey && !event.altKey && event.key.length === 1) {
        pending = { key: event.key.toLowerCase(), at: Date.now() };
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled]);
}

/**
 * Predictive back: while an overlay is open, the system back gesture (or button) closes it
 * instead of leaving the page. Opening pushes a history entry; closing by hand removes it.
 */
export function useBackToClose(open: boolean, close: () => void): void {
  const closeRef = useRef(close);
  closeRef.current = close;

  useEffect(() => {
    if (!open) return undefined;
    const marker = { riarideOverlay: Math.random() };
    window.history.pushState(marker, '');
    let closedByBack = false;
    const onPopState = () => {
      closedByBack = true;
      closeRef.current();
    };
    window.addEventListener('popstate', onPopState);
    return () => {
      window.removeEventListener('popstate', onPopState);
      const state: unknown = window.history.state;
      if (
        !closedByBack &&
        typeof state === 'object' &&
        state !== null &&
        'riarideOverlay' in state
      ) {
        window.history.back();
      }
    };
  }, [open]);
}

function subscribeMedia(query: string) {
  return (callback: () => void) => {
    if (typeof window.matchMedia !== 'function') return () => undefined;
    const list = window.matchMedia(query);
    list.addEventListener('change', callback);
    return () => list.removeEventListener('change', callback);
  };
}

export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    subscribeMedia(query),
    () => (typeof window.matchMedia === 'function' ? window.matchMedia(query).matches : false),
    () => false,
  );
}
