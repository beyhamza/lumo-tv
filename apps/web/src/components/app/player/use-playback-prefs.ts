"use client";

import { useCallback, useEffect, useMemo, useSyncExternalStore } from "react";
import {
  type PlaybackPrefs,
  browserStorage,
  defaultPrefs,
  loadPrefs,
  parsePrefs,
  prefsKey,
  savePrefs,
} from "@/lib/playback/preferences";

/**
 * The playback preferences of one account on this browser, as React state
 * (S13-02). See `lib/playback/preferences.ts` for the rules.
 *
 * Read through `useSyncExternalStore` on the raw stored string — a string
 * compares by value, so the snapshot is stable without a cache — and kept in
 * step across tabs by the `storage` event. On the server, and during
 * hydration, it is `null`: preferences are a browser's, and a server render
 * guessing them would be a mismatch waiting to happen.
 */
const UNAVAILABLE = "\u0000unavailable";
const local = new Set<() => void>();

function subscribe(listener: () => void) {
  local.add(listener);
  window.addEventListener("storage", listener);
  return () => {
    local.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

function notify() {
  for (const listener of local) listener();
}

export type PrefsHandle = {
  /** Null until read in the browser. */
  prefs: PlaybackPrefs | null;
  /** False when the browser refuses storage: the defaults apply, nothing is kept. */
  persistent: boolean;
  /** Merges and writes; true only when the browser accepted the write. */
  update: (change: Partial<PlaybackPrefs>) => boolean;
};

export function usePlaybackPrefs(accountId: string, uiLocale: string): PrefsHandle {
  const raw = useSyncExternalStore(
    subscribe,
    () => {
      const storage = browserStorage();
      if (!storage || !accountId) return UNAVAILABLE;
      try {
        return storage.getItem(prefsKey(accountId));
      } catch {
        return UNAVAILABLE;
      }
    },
    () => undefined,
  );

  // First initialisation (Q6): the interface language of this moment is kept.
  useEffect(() => {
    if (raw === null) {
      loadPrefs(browserStorage(), accountId, uiLocale);
      notify();
    }
  }, [raw, accountId, uiLocale]);

  const prefs = useMemo(() => {
    if (raw === undefined) return null;
    const fallback = defaultPrefs(uiLocale);
    return raw === null || raw === UNAVAILABLE ? fallback : parsePrefs(raw, fallback);
  }, [raw, uiLocale]);

  const update = useCallback(
    (change: Partial<PlaybackPrefs>) => {
      const base = prefs ?? defaultPrefs(uiLocale);
      const saved = savePrefs(browserStorage(), accountId, { ...base, ...change });
      notify();
      return saved;
    },
    [prefs, accountId, uiLocale],
  );

  return { prefs, persistent: raw !== UNAVAILABLE, update };
}
