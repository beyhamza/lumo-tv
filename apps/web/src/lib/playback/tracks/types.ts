import type { QualityLevel, Track } from "../track-choice";

/**
 * What a web player can say about its tracks and variants, **as the browser
 * or hls.js actually exposes them** (US-023, S13-01).
 *
 * Every list has an honest "we cannot tell" state beside the listed one: a
 * browser that does not report the audio tracks of a file has not proven that
 * there is only one, and the menu says so rather than "no other track" (Q6:
 * "Si le lecteur ne permet pas de connaître les pistes, annoncer cette limite
 * plutôt que « aucune piste »").
 */
export type TrackListing =
  | { state: "listed"; tracks: Track[]; selected: string | null }
  | { state: "unknown"; reason: UnknownReason };

export type UnknownReason =
  /** The element does not report the tracks inside a file (Chromium, Firefox). */
  | "browser-hides-tracks"
  /** An HLS stream with no alternative audio rendition declared: its muxed audio plays. */
  | "not-declared"
  /** Nothing loaded yet. */
  | "loading";

export type QualityListing =
  | { state: "listed"; levels: QualityLevel[]; selected: string | "auto" }
  | { state: "auto-only"; reason: AutoOnlyReason };

export type AutoOnlyReason =
  /** A progressive file: one encoding, nothing to choose. */
  | "single-file"
  /** Native HLS (Safari/iOS without MSE): the browser picks and exposes no control. */
  | "browser-chooses"
  /** An HLS stream with one variant. */
  | "single-variant"
  | "loading";

export type TrackSnapshot = {
  audio: TrackListing;
  subtitles: TrackListing;
  quality: QualityListing;
};

/**
 * One player's tracks, behind one interface whatever exposes them.
 *
 * `select*` resolve to `true` only once the player itself reports the new
 * selection, `false` on refusal or after a timeout — the menu confirms what
 * the player confirmed, never what was asked (Q6: "Ne confirmer la sélection
 * effective qu'après retour du lecteur").
 */
export interface TrackController {
  snapshot(): TrackSnapshot;
  subscribe(listener: () => void): () => void;
  selectAudio(id: string): Promise<boolean>;
  selectSubtitles(id: string | null): Promise<boolean>;
  selectQuality(id: string | "auto"): Promise<boolean>;
  dispose(): void;
}

export const SWITCH_TIMEOUT_MS = 8_000;

/** A tiny listener set, shared by both adapters. */
export function listeners() {
  const set = new Set<() => void>();
  return {
    add(listener: () => void) {
      set.add(listener);
      return () => {
        set.delete(listener);
      };
    },
    emit() {
      for (const listener of set) listener();
    },
    clear() {
      set.clear();
    },
  };
}
