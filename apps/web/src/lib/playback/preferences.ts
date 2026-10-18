/**
 * Playback preferences, local to this browser and to one account (US-023,
 * S13-02, Q6 in docs/design/0.2.0/player-settings-cases.md).
 *
 * <h2>Why `localStorage` and not a cookie</h2>
 *
 * Nothing on the server ever needs these values: the tracks are chosen by the
 * player, in the browser, from what the media element or hls.js reports, and
 * the next-episode countdown runs there too. A cookie would travel with every
 * request of the account zone for no reader, and the API never sees them
 * either (no endpoint, no sync — Q6: "Les préférences de lecture ne se
 * synchronisent pas entre appareils"). A browser is its own context, which is
 * exactly what `localStorage` is.
 *
 * <h2>One entry per account</h2>
 *
 * Keyed by the account id (`lumo.playback.v1.<accountId>`): account B on this
 * browser never reads A's languages, and A coming back finds them again (PS-02).
 * Nothing is purged at sign-out on purpose — Q6 asks for exactly that return.
 *
 * <h2>Storage can fail, and that is said</h2>
 *
 * Private windows, blocked site data and full quotas all throw. Reading falls
 * back to the defaults; writing reports `false`, and the caller says "applied
 * for this playback, preference not saved" rather than claiming a save (Q6:
 * "Une préférence locale ne doit pas être annoncée enregistrée si son écriture
 * échoue").
 *
 * <h2>Defaults (Q6)</h2>
 *
 * Audio and subtitle languages start as the **interface language at first
 * initialisation**, and are written then, so that switching the interface
 * later changes neither (PS-01). Subtitles start off; autoplay next starts on.
 * Manual quality is never a preference: it belongs to the current playback.
 */

export type PlaybackPrefs = {
  /** ISO 639-1 code, e.g. `fr`. */
  audioLanguage: string;
  subtitlesEnabled: boolean;
  /** Kept while subtitles are off: turning them back on finds it again. */
  subtitleLanguage: string;
  autoplayNext: boolean;
};

/** The part of `Storage` used here, so that tests can hand a map. */
export type PrefsStorage = Pick<Storage, "getItem" | "setItem">;

const KEY_PREFIX = "lumo.playback.v1.";

export function prefsKey(accountId: string): string {
  return `${KEY_PREFIX}${accountId}`;
}

/**
 * Languages offered in Settings → Playback. A closed list rather than every
 * ISO code: a select of 180 entries is not a choice anybody can make, and a
 * track whose language is not listed here still plays — matching works on any
 * code the stream declares.
 */
export const PREFERENCE_LANGUAGES = [
  "fr",
  "en",
  "ar",
  "de",
  "es",
  "it",
  "nl",
  "pl",
  "pt",
  "ro",
  "ru",
  "tr",
] as const;

export function defaultPrefs(uiLocale: string): PlaybackPrefs {
  const language = normalizeLanguage(uiLocale) ?? "en";
  return {
    audioLanguage: language,
    subtitlesEnabled: false,
    subtitleLanguage: language,
    autoplayNext: true,
  };
}

/**
 * The stored preferences, or the defaults — **written on first read** so that
 * the interface language at that moment is the one kept (PS-01).
 *
 * Never throws. A malformed entry is replaced field by field: a value of the
 * wrong type is a default, not a crash of the player.
 */
export function loadPrefs(
  storage: PrefsStorage | null,
  accountId: string,
  uiLocale: string,
): { prefs: PlaybackPrefs; stored: boolean } {
  const fallback = defaultPrefs(uiLocale);
  if (!storage || !accountId) return { prefs: fallback, stored: false };

  let raw: string | null;
  try {
    raw = storage.getItem(prefsKey(accountId));
  } catch {
    return { prefs: fallback, stored: false };
  }

  if (raw === null) {
    // First initialisation: the interface language is fixed now.
    return { prefs: fallback, stored: savePrefs(storage, accountId, fallback) };
  }

  return { prefs: parsePrefs(raw, fallback), stored: true };
}

export function parsePrefs(raw: string, fallback: PlaybackPrefs): PlaybackPrefs {
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return fallback;
  }
  if (typeof value !== "object" || value === null) return fallback;
  const record = value as Record<string, unknown>;
  return {
    audioLanguage: languageOr(record.audioLanguage, fallback.audioLanguage),
    subtitlesEnabled:
      typeof record.subtitlesEnabled === "boolean"
        ? record.subtitlesEnabled
        : fallback.subtitlesEnabled,
    subtitleLanguage: languageOr(record.subtitleLanguage, fallback.subtitleLanguage),
    autoplayNext:
      typeof record.autoplayNext === "boolean" ? record.autoplayNext : fallback.autoplayNext,
  };
}

/** True only when the browser accepted the write. */
export function savePrefs(
  storage: PrefsStorage | null,
  accountId: string,
  prefs: PlaybackPrefs,
): boolean {
  if (!storage || !accountId) return false;
  try {
    storage.setItem(prefsKey(accountId), JSON.stringify(prefs));
    return true;
  } catch {
    return false;
  }
}

/** `window.localStorage`, or null where merely touching it throws. */
export function browserStorage(): PrefsStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    return null;
  }
}

function languageOr(value: unknown, fallback: string): string {
  return (typeof value === "string" && normalizeLanguage(value)) || fallback;
}

/**
 * ISO 639-2 codes streams commonly carry, mapped to the ISO 639-1 codes used
 * everywhere else here. Both bibliographic (`fre`) and terminologic (`fra`)
 * forms appear in the wild.
 */
const THREE_LETTER: Record<string, string> = {
  fre: "fr",
  fra: "fr",
  eng: "en",
  ara: "ar",
  ger: "de",
  deu: "de",
  spa: "es",
  ita: "it",
  dut: "nl",
  nld: "nl",
  pol: "pl",
  por: "pt",
  rum: "ro",
  ron: "ro",
  rus: "ru",
  tur: "tr",
  jpn: "ja",
  chi: "zh",
  zho: "zh",
  kor: "ko",
  hin: "hi",
  gre: "el",
  ell: "el",
  swe: "sv",
  nor: "no",
  dan: "da",
  fin: "fi",
  heb: "he",
  ukr: "uk",
  cze: "cs",
  ces: "cs",
  hun: "hu",
  per: "fa",
  fas: "fa",
};

/**
 * A language tag as streams write it (`fr`, `fr-CA`, `fre`, `FRA`, `pt_BR`)
 * reduced to a comparable code, or null for none (`und`, `mul`, `zxx`, empty).
 *
 * An unknown three-letter code is kept as-is: it compares equal to itself,
 * which is all that matching needs.
 */
export function normalizeLanguage(tag: string | null | undefined): string | null {
  if (!tag) return null;
  const primary = tag.trim().toLowerCase().split(/[-_]/)[0];
  if (!primary || !/^[a-z]{2,3}$/.test(primary)) return null;
  if (primary === "und" || primary === "mul" || primary === "zxx" || primary === "mis") {
    return null;
  }
  if (primary.length === 3) return THREE_LETTER[primary] ?? primary;
  return primary;
}
