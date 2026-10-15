import { describe, expect, it } from "vitest";
import {
  nextAfter,
  orderedEpisodeIds,
  playerActions,
  seriesContinuation,
  sheetChoice,
} from "./sheet-action";

const MIN = 60_000;

describe("sheetChoice", () => {
  it("offers Watch with no progress", () => {
    expect(sheetChoice(undefined)).toEqual({ kind: "watch" });
    expect(sheetChoice(null)).toEqual({ kind: "watch" });
    expect(sheetChoice({ position_ms: 0, duration_ms: 100 * MIN })).toEqual({ kind: "watch" });
  });

  it("offers Resume short of the end", () => {
    expect(sheetChoice({ position_ms: 20 * MIN, duration_ms: 100 * MIN })).toEqual({
      kind: "resume",
      atMs: 20 * MIN,
    });
  });

  it("offers Resume exactly at 95 % — not finished yet (CW-06)", () => {
    expect(sheetChoice({ position_ms: 95 * MIN, duration_ms: 100 * MIN }).kind).toBe("resume");
  });

  it("offers Watch again past 95 % (CW-07)", () => {
    expect(sheetChoice({ position_ms: 96 * MIN, duration_ms: 100 * MIN })).toEqual({
      kind: "rewatch",
    });
  });

  it("offers Resume with an unknown duration (CW-09)", () => {
    expect(sheetChoice({ position_ms: 300 * MIN, duration_ms: null }).kind).toBe("resume");
  });

  it("still offers Resume for an item removed from Continue (CW-10)", () => {
    // The row the server returns for a hidden item: position kept.
    const hidden = { position_ms: 12 * MIN, duration_ms: 100 * MIN, hidden: true };
    expect(sheetChoice(hidden)).toEqual({ kind: "resume", atMs: 12 * MIN });
  });
});

describe("playerActions", () => {
  const words = {
    watch: "Watch",
    resumeAt: (ms: number) => `Resume at ${ms}`,
    rewatch: "Watch again",
    restart: "Start over",
  };

  it("pairs Resume with Start over", () => {
    expect(playerActions({ kind: "resume", atMs: 42 }, words)).toEqual({
      primary: { label: "Resume at 42", fromMs: 42 },
      secondary: { label: "Start over", fromMs: 0 },
    });
  });

  it("gives Watch and Watch again a single button from zero", () => {
    expect(playerActions({ kind: "watch" }, words)).toEqual({
      primary: { label: "Watch", fromMs: 0 },
      secondary: null,
    });
    expect(playerActions({ kind: "rewatch" }, words)).toEqual({
      primary: { label: "Watch again", fromMs: 0 },
      secondary: null,
    });
  });
});

describe("seriesContinuation", () => {
  const order = ["e1", "e2", "e3", "e4"];
  const row = (ref: string, positionMin: number, updatedAt: string, durationMin = 40) => ({
    item_ref: ref,
    position_ms: positionMin * MIN,
    duration_ms: durationMin * MIN,
    updated_at: updatedAt,
  });

  it("is null when nothing of this series was saved", () => {
    expect(seriesContinuation([row("other", 5, "2026-10-01T00:00:00Z")], order)).toBeNull();
  });

  it("resumes the most recent unfinished episode", () => {
    const rows = [row("e1", 10, "2026-10-01T00:00:00Z"), row("e3", 12, "2026-10-02T00:00:00Z")];
    expect(seriesContinuation(rows, order)).toMatchObject({
      episodeId: "e3",
      kind: "resume",
      startMs: 12 * MIN,
    });
  });

  it("proposes the next episode, never started, from zero (CW-19)", () => {
    const rows = [row("e2", 39, "2026-10-02T00:00:00Z")];
    expect(seriesContinuation(rows, order)).toEqual({
      episodeId: "e3",
      kind: "next",
      startMs: 0,
      row: null,
    });
  });

  it("resumes a next episode already started (CW-12)", () => {
    const rows = [row("e3", 7, "2026-09-01T00:00:00Z"), row("e2", 39, "2026-10-02T00:00:00Z")];
    expect(seriesContinuation(rows, order)).toMatchObject({
      episodeId: "e3",
      kind: "next",
      startMs: 7 * MIN,
    });
  });

  it("replays a next episode already finished, without skipping ahead (CW-21)", () => {
    const rows = [row("e3", 40, "2026-09-01T00:00:00Z"), row("e2", 39, "2026-10-02T00:00:00Z")];
    expect(seriesContinuation(rows, order)).toMatchObject({ episodeId: "e3", startMs: 0 });
  });

  it("is null after the last episode (CW-15)", () => {
    expect(seriesContinuation([row("e4", 40, "2026-10-02T00:00:00Z")], order)).toBeNull();
  });

  it("does not treat an episode unknown to the tree as next", () => {
    expect(nextAfter("missing", order, [])).toBeNull();
  });
});

describe("orderedEpisodeIds", () => {
  it("orders seasons then episodes by number", () => {
    expect(
      orderedEpisodeIds([
        {
          season_number: 2,
          episodes: [
            { id: "s2e2", episode_number: 2 },
            { id: "s2e1", episode_number: 1 },
          ],
        },
        { season_number: 1, episodes: [{ id: "s1e1", episode_number: 1 }] },
      ]),
    ).toEqual(["s1e1", "s2e1", "s2e2"]);
  });
});
