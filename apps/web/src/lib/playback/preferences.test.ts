import { describe, expect, it } from "vitest";
import {
  type PrefsStorage,
  defaultPrefs,
  loadPrefs,
  normalizeLanguage,
  prefsKey,
  savePrefs,
} from "./preferences";

/** A storage that behaves, and two that do not — the private window and the full quota. */
function memory(): PrefsStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
  };
}
const refusing: PrefsStorage = {
  getItem: () => {
    throw new Error("SecurityError");
  },
  setItem: () => {
    throw new Error("SecurityError");
  },
};
const full: PrefsStorage = {
  getItem: () => null,
  setItem: () => {
    throw new Error("QuotaExceededError");
  },
};

const A = "11111111-1111-4111-8111-111111111111";
const B = "22222222-2222-4222-8222-222222222222";

describe("defaults (Q6)", () => {
  it("start from the interface language, subtitles off, autoplay on", () => {
    expect(defaultPrefs("fr")).toEqual({
      audioLanguage: "fr",
      subtitlesEnabled: false,
      subtitleLanguage: "fr",
      autoplayNext: true,
    });
  });

  it("are written at first read, so a later interface change leaves them (PS-01)", () => {
    const storage = memory();
    expect(loadPrefs(storage, A, "fr")).toEqual({ prefs: defaultPrefs("fr"), stored: true });
    // The interface switched to English afterwards: the playback languages stay French.
    expect(loadPrefs(storage, A, "en").prefs.audioLanguage).toBe("fr");
    expect(loadPrefs(storage, A, "en").prefs.subtitleLanguage).toBe("fr");
  });
});

describe("one entry per account on this browser (PS-02)", () => {
  it("keeps A's choices away from B, and gives them back to A", () => {
    const storage = memory();
    loadPrefs(storage, A, "fr");
    savePrefs(storage, A, { ...defaultPrefs("fr"), audioLanguage: "es", autoplayNext: false });

    const b = loadPrefs(storage, B, "en").prefs;
    expect(b.audioLanguage).toBe("en");
    expect(b.autoplayNext).toBe(true);

    const a = loadPrefs(storage, A, "en").prefs;
    expect(a.audioLanguage).toBe("es");
    expect(a.autoplayNext).toBe(false);
  });

  it("keys by account id", () => {
    expect(prefsKey(A)).toBe(`lumo.playback.v1.${A}`);
  });
});

describe("storage that fails is said, never thrown", () => {
  it("falls back to the defaults when reading is refused", () => {
    expect(loadPrefs(refusing, A, "en")).toEqual({ prefs: defaultPrefs("en"), stored: false });
  });

  it("reports a refused write as not saved", () => {
    expect(savePrefs(full, A, defaultPrefs("fr"))).toBe(false);
    expect(savePrefs(refusing, A, defaultPrefs("fr"))).toBe(false);
    expect(loadPrefs(full, A, "fr").stored).toBe(false);
  });

  it("has nothing to save without storage or account", () => {
    expect(savePrefs(null, A, defaultPrefs("fr"))).toBe(false);
    expect(savePrefs(memory(), "", defaultPrefs("fr"))).toBe(false);
  });

  it("repairs a malformed entry field by field", () => {
    const storage = memory();
    storage.data.set(prefsKey(A), JSON.stringify({ audioLanguage: 42, autoplayNext: false }));
    expect(loadPrefs(storage, A, "fr").prefs).toEqual({ ...defaultPrefs("fr"), autoplayNext: false });
    storage.data.set(prefsKey(A), "{not json");
    expect(loadPrefs(storage, A, "fr").prefs).toEqual(defaultPrefs("fr"));
  });
});

describe("normalizeLanguage", () => {
  it("reduces the tags streams carry to one comparable code", () => {
    expect(normalizeLanguage("fr")).toBe("fr");
    expect(normalizeLanguage("fr-CA")).toBe("fr");
    expect(normalizeLanguage("FRE")).toBe("fr");
    expect(normalizeLanguage("fra")).toBe("fr");
    expect(normalizeLanguage("pt_BR")).toBe("pt");
    expect(normalizeLanguage("eng")).toBe("en");
  });

  it("knows no language for und, mul, zxx or rubbish", () => {
    for (const tag of ["und", "mul", "zxx", "", null, undefined, "Français", "12"]) {
      expect(normalizeLanguage(tag)).toBeNull();
    }
  });

  it("keeps an unknown three-letter code as itself", () => {
    expect(normalizeLanguage("tlh")).toBe("tlh");
  });
});
