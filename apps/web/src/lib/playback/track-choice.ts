import { normalizeLanguage } from "./preferences";

/**
 * Which track to select at open, from the preferences and what the player
 * reports (US-023, S13-01/02, rules "Pistes et sous-titres" of
 * docs/design/0.2.0/player-settings-cases.md).
 *
 * Pure: the adapters (`tracks/native.ts`, `tracks/hls.ts`) turn whatever the
 * browser or hls.js exposes into `Track`s, and this decides. The identifiers
 * are the player's own for **this** media: nothing here, and nothing calling
 * it, carries one from one content to the next (PS-06).
 */
export type Track = {
  /** The player's identifier for this track, in this media only. */
  id: string;
  /** As the stream declares it — normalised before any comparison. */
  language: string | null;
  /** A name the stream provides, if any. */
  name: string | null;
  isDefault: boolean;
  /** Only when the metadata says so; never inferred from a name (Q6). */
  forced: boolean;
  /** False for a track identified but not playable here: listed, not selectable. */
  supported: boolean;
};

export type AudioChoice =
  | { id: string; reason: "preferred" | "default" | "first-playable" }
  | { id: null; reason: "none-playable" };

/**
 * The audio track to start with.
 *
 * 1. A playable track in the preferred language — the one marked default
 *    first, then the player's order.
 * 2. Otherwise the playable default track (a fallback: the preference is
 *    **not** overwritten by it — the caller does not save anything here).
 * 3. Otherwise the first playable track.
 * 4. Otherwise nothing: the player says it cannot play any, rather than
 *    inventing a track.
 */
export function chooseAudio(tracks: readonly Track[], preferred: string | null): AudioChoice {
  const playable = tracks.filter((track) => track.supported);
  const wanted = normalizeLanguage(preferred);
  if (wanted) {
    const matching = playable.filter((track) => normalizeLanguage(track.language) === wanted);
    const pick = matching.find((track) => track.isDefault) ?? matching[0];
    if (pick) return { id: pick.id, reason: "preferred" };
  }
  const byDefault = playable.find((track) => track.isDefault);
  if (byDefault) return { id: byDefault.id, reason: "default" };
  if (playable[0]) return { id: playable[0].id, reason: "first-playable" };
  return { id: null, reason: "none-playable" };
}

export type SubtitleChoice =
  | { id: null; reason: "off" | "language-unavailable" }
  | { id: string; reason: "complete" | "forced-only" };

/**
 * The subtitle track to start with.
 *
 * - Off: **no** text track, a forced one included (Q6). Burnt-in subtitles are
 *   pixels, out of reach, and nothing here pretends otherwise.
 * - On: the preferred language — a complete track first (default, then
 *   order), then a forced one if it is all there is.
 * - On, language absent: nothing. **No other language is switched on**, and
 *   the preference is left as it is; the caller says the language is missing.
 */
export function chooseSubtitles(
  tracks: readonly Track[],
  enabled: boolean,
  preferred: string | null,
): SubtitleChoice {
  if (!enabled) return { id: null, reason: "off" };
  const wanted = normalizeLanguage(preferred);
  const matching = tracks.filter(
    (track) => track.supported && wanted !== null && normalizeLanguage(track.language) === wanted,
  );
  const complete = matching.filter((track) => !track.forced);
  const pickComplete = complete.find((track) => track.isDefault) ?? complete[0];
  if (pickComplete) return { id: pickComplete.id, reason: "complete" };
  const forced = matching.find((track) => track.forced);
  if (forced) return { id: forced.id, reason: "forced-only" };
  return { id: null, reason: "language-unavailable" };
}

/**
 * How a track is named in a menu, before translation.
 *
 * A language when one is declared; otherwise "Track N" by its position among
 * the tracks of the same list (Q6), the stream's own name added when it says
 * something the language does not. "Forced" only when the metadata says so.
 * Two tracks of the same language get their position too, so no two lines of
 * a menu read the same (PS-06).
 */
export type TrackLabel = {
  language: string | null;
  /** 1-based position in the list; shown when the language alone is not enough. */
  number: number | null;
  name: string | null;
  forced: boolean;
};

export function trackLabels(tracks: readonly Track[]): TrackLabel[] {
  const counts = new Map<string, number>();
  for (const track of tracks) {
    const language = normalizeLanguage(track.language);
    if (language) counts.set(language, (counts.get(language) ?? 0) + 1);
  }
  return tracks.map((track, index) => {
    const language = normalizeLanguage(track.language);
    const ambiguous = language === null || (counts.get(language) ?? 0) > 1;
    const name = track.name?.trim() || null;
    return {
      language,
      number: ambiguous ? index + 1 : null,
      // A name that only repeats the language adds nothing to the line.
      name: name && (language === null || normalizeLanguage(name) !== language) ? name : null,
      forced: track.forced,
    };
  });
}

/** A variant hls.js can switch to, as much as a label needs. */
export type QualityLevel = { id: string; height: number | null; bitrate: number | null };

/**
 * Quality labels: the height when known (`720p`), the bitrate beside it when
 * two variants share a height, the bitrate alone when there is no height.
 * Highest first, the way people read a quality list.
 */
export function qualityOptions(
  levels: readonly QualityLevel[],
): { id: string; height: number | null; kbps: number | null }[] {
  const heights = new Map<number, number>();
  for (const level of levels) {
    if (level.height) heights.set(level.height, (heights.get(level.height) ?? 0) + 1);
  }
  return [...levels]
    .sort((a, b) => (b.height ?? 0) - (a.height ?? 0) || (b.bitrate ?? 0) - (a.bitrate ?? 0))
    .map((level) => {
      const shared = level.height !== null && (heights.get(level.height) ?? 0) > 1;
      const kbps = level.bitrate ? Math.round(level.bitrate / 1000) : null;
      return {
        id: level.id,
        height: level.height || null,
        kbps: !level.height || shared ? kbps : null,
      };
    });
}
