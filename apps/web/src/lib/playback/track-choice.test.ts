import { describe, expect, it } from "vitest";
import { type Track, chooseAudio, chooseSubtitles, qualityOptions, trackLabels } from "./track-choice";

function track(id: string, language: string | null, extra: Partial<Track> = {}): Track {
  return { id, language, name: null, isDefault: false, forced: false, supported: true, ...extra };
}

describe("chooseAudio (PS-04)", () => {
  it("takes the preferred language, the default one of it first", () => {
    const tracks = [track("0", "eng", { isDefault: true }), track("1", "fre"), track("2", "fr", { isDefault: true })];
    expect(chooseAudio(tracks, "fr")).toEqual({ id: "2", reason: "preferred" });
  });

  it("falls back to the default track when the language is absent", () => {
    const tracks = [track("0", "en"), track("1", "de", { isDefault: true })];
    expect(chooseAudio(tracks, "fr")).toEqual({ id: "1", reason: "default" });
  });

  it("then to the first playable track", () => {
    expect(chooseAudio([track("0", "en", { supported: false }), track("1", "de")], "fr")).toEqual({
      id: "1",
      reason: "first-playable",
    });
  });

  it("skips a preferred track the device cannot play", () => {
    const tracks = [track("0", "fr", { supported: false }), track("1", "en", { isDefault: true })];
    expect(chooseAudio(tracks, "fr")).toEqual({ id: "1", reason: "default" });
  });

  it("invents nothing when no track is playable", () => {
    expect(chooseAudio([track("0", "fr", { supported: false })], "fr")).toEqual({
      id: null,
      reason: "none-playable",
    });
  });
});

describe("chooseSubtitles (PS-04, PS-05)", () => {
  const tracks = [
    track("0", "fr", { forced: true, isDefault: true }),
    track("1", "en"),
    track("2", "fr"),
  ];

  it("selects nothing when off — a forced track included", () => {
    expect(chooseSubtitles(tracks, false, "fr")).toEqual({ id: null, reason: "off" });
  });

  it("prefers a complete track over a forced one", () => {
    expect(chooseSubtitles(tracks, true, "fr")).toEqual({ id: "2", reason: "complete" });
  });

  it("takes the forced track when it is all the language has", () => {
    expect(chooseSubtitles([track("0", "fr", { forced: true }), track("1", "en")], true, "fr")).toEqual({
      id: "0",
      reason: "forced-only",
    });
  });

  it("never switches on another language", () => {
    expect(chooseSubtitles([track("0", "en", { isDefault: true })], true, "fr")).toEqual({
      id: null,
      reason: "language-unavailable",
    });
  });

  it("finds the language when the next content has it", () => {
    // Same preference, next content: absent once, present now (PS-04).
    expect(chooseSubtitles([track("7", "fre")], true, "fr")).toEqual({ id: "7", reason: "complete" });
  });
});

describe("trackLabels (PS-06)", () => {
  it("numbers tracks without a language, and duplicates of one", () => {
    const labels = trackLabels([
      track("0", null),
      track("1", "fr"),
      track("2", "fr", { name: "Commentaires" }),
      track("3", "en", { name: "English" }),
      track("4", "und", { forced: true }),
    ]);
    expect(labels).toEqual([
      { language: null, number: 1, name: null, forced: false },
      { language: "fr", number: 2, name: null, forced: false },
      { language: "fr", number: 3, name: "Commentaires", forced: false },
      // The name the stream gives is kept: only a bare code ("en", "eng") is dropped.
      { language: "en", number: null, name: "English", forced: false },
      { language: null, number: 5, name: null, forced: true },
    ]);
  });
});

describe("qualityOptions", () => {
  it("lists highest first, with the bitrate only where heights collide", () => {
    expect(
      qualityOptions([
        { id: "0", height: 360, bitrate: 800_000 },
        { id: "1", height: 720, bitrate: 2_500_000 },
        { id: "2", height: 720, bitrate: 3_500_000 },
        { id: "3", height: null, bitrate: 128_000 },
      ]),
    ).toEqual([
      { id: "2", height: 720, kbps: 3500 },
      { id: "1", height: 720, kbps: 2500 },
      { id: "0", height: 360, kbps: null },
      { id: "3", height: null, kbps: 128 },
    ]);
  });
});
