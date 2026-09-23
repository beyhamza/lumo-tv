import { describe, expect, it } from "vitest";
import type { EpgGrid, EpgProgramme } from "@/lib/api/types";
import {
  CHANNELS_PER_REQUEST,
  MAX_CONCURRENT,
  MAX_SPLIT_DEPTH,
  fetchBounded,
  type BatchAnswer,
  type BatchRequest,
} from "./split";

/**
 * The bounded split (C1, D2).
 *
 * What is under test is the part nobody sees on screen: how many requests
 * leave, how many at once, in what shape, and whether the programme spanning a
 * cut comes back once. A split that sends four requests at a time renders the
 * same grid as one that sends two.
 */

const from = new Date("2026-09-24T18:00:00Z");
const to = new Date("2026-09-24T21:00:00Z");

const ids = (count: number, prefix = "c") =>
  Array.from({ length: count }, (_, index) => `${prefix}${String(index).padStart(3, "0")}`);

const status = {
  configured: true,
  last_successful_import_at: "2026-09-24T06:00:00Z",
  last_attempt_started_at: "2026-09-24T05:55:00Z",
  last_attempt_finished_at: "2026-09-24T06:00:00Z",
  last_attempt_status: "SUCCEEDED",
} as const;

function programme(id: string, startsAt: string, endsAt: string): EpgProgramme {
  return {
    id,
    source_id: "s",
    tvg_id: `tvg-${id}`,
    starts_at: startsAt,
    ends_at: endsAt,
    title: `Programme ${id}`,
  };
}

/** A complete answer for a batch: one entry per id, programmes by a rule. */
function grid(
  batch: BatchRequest,
  programmesOf: (channelId: string, batch: BatchRequest) => EpgProgramme[] = () => [],
): EpgGrid {
  return {
    source_id: "s",
    from: batch.from.toISOString(),
    to: batch.to.toISOString(),
    generated_at: "2026-09-24T18:00:01Z",
    epg: status,
    channels: batch.channelIds.map((channelId) => ({
      channel_id: channelId,
      mapping_status: "MAPPED",
      programmes: programmesOf(channelId, batch),
    })),
  };
}

/**
 * A fetcher that records every request, tracks how many are in flight at
 * once, and answers by a rule. Answers are deferred a tick so that two
 * requests can actually overlap.
 */
function fake(rule: (batch: BatchRequest) => BatchAnswer) {
  const asked: BatchRequest[] = [];
  let inFlight = 0;
  let peak = 0;
  const fetch = async (batch: BatchRequest): Promise<BatchAnswer> => {
    asked.push(batch);
    inFlight += 1;
    peak = Math.max(peak, inFlight);
    await new Promise((resolve) => setTimeout(resolve, 1));
    inFlight -= 1;
    return rule(batch);
  };
  return { fetch, asked, peak: () => peak };
}

describe("fetchBounded", () => {
  it("sends one request for a batch under the ceiling, and hands the rows back in order", async () => {
    const channelIds = ["b", "a", "c"];
    const { fetch, asked } = fake((batch) => ({ kind: "ok", grid: grid(batch) }));

    const result = await fetchBounded({ channelIds, from, to }, fetch);

    expect(asked).toHaveLength(1);
    expect(asked[0].channelIds).toEqual(["b", "a", "c"]);
    expect(result.kind).toBe("ok");
    if (result.kind !== "ok") return;
    expect(result.channels.map((row) => row.channel_id)).toEqual(["b", "a", "c"]);
    expect(result.epg).toEqual(status);
    expect(result.generatedAt).toBe("2026-09-24T18:00:01Z");
  });

  it("drops duplicate identifiers before asking: the server refuses a duplicate", async () => {
    const { fetch, asked } = fake((batch) => ({ kind: "ok", grid: grid(batch) }));

    const result = await fetchBounded({ channelIds: ["a", "b", "a"], from, to }, fetch);

    expect(asked[0].channelIds).toEqual(["a", "b"]);
    expect(result.kind === "ok" && result.channels.map((row) => row.channel_id)).toEqual([
      "a",
      "b",
    ]);
  });

  it("chunks above the contract's ceiling, two at a time", async () => {
    const { fetch, asked, peak } = fake((batch) => ({ kind: "ok", grid: grid(batch) }));

    const result = await fetchBounded({ channelIds: ids(250), from, to }, fetch);

    expect(asked.map((batch) => batch.channelIds.length)).toEqual([
      CHANNELS_PER_REQUEST,
      CHANNELS_PER_REQUEST,
      50,
    ]);
    expect(peak()).toBe(MAX_CONCURRENT);
    expect(result.kind === "ok" && result.channels).toHaveLength(250);
  });

  it("halves the channels first when the server refuses the batch as too large", async () => {
    const { fetch, asked } = fake((batch) =>
      batch.channelIds.length > 5 ? { kind: "too-large" } : { kind: "ok", grid: grid(batch) },
    );

    const result = await fetchBounded({ channelIds: ids(10), from, to }, fetch);

    // 10 → 5 + 5. The window is untouched: it is the channels that are cut.
    expect(asked.map((batch) => batch.channelIds.length)).toEqual([10, 5, 5]);
    expect(asked.every((batch) => batch.from === from && batch.to === to)).toBe(true);
    expect(result.kind === "ok" && result.channels.map((row) => row.channel_id)).toEqual(
      ids(10),
    );
  });

  it("halves the window only once a single channel is still too large", async () => {
    const { fetch, asked } = fake((batch) =>
      batch.to.getTime() - batch.from.getTime() > 90 * 60_000
        ? { kind: "too-large" }
        : { kind: "ok", grid: grid(batch) },
    );

    const result = await fetchBounded({ channelIds: ["only"], from, to }, fetch);

    expect(asked.map((batch) => [batch.from.toISOString(), batch.to.toISOString()])).toEqual([
      ["2026-09-24T18:00:00.000Z", "2026-09-24T21:00:00.000Z"],
      ["2026-09-24T18:00:00.000Z", "2026-09-24T19:30:00.000Z"],
      ["2026-09-24T19:30:00.000Z", "2026-09-24T21:00:00.000Z"],
    ]);
    expect(result.kind).toBe("ok");
  });

  it("merges a programme spanning the cut once, in the contract's order", async () => {
    const spanning = programme("span", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z");
    const early = programme("early", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z");
    const late = programme("late", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z");
    const { fetch } = fake((batch) => {
      const whole = batch.to.getTime() - batch.from.getTime() === 3 * 60 * 60_000;
      if (whole) return { kind: "too-large" };
      // Each half returns what overlaps it; the spanning programme overlaps both.
      const firstHalf = batch.from.getTime() === from.getTime();
      return {
        kind: "ok",
        grid: grid(batch, () => (firstHalf ? [early, spanning] : [spanning, late])),
      };
    });

    const result = await fetchBounded({ channelIds: ["only"], from, to }, fetch);

    expect(result.kind === "ok" && result.channels[0].programmes.map((p) => p.id)).toEqual([
      "early",
      "span",
      "late",
    ]);
  });

  it("does not de-duplicate across channels: two rows sharing a tvg_id both keep the programme", async () => {
    const shared = programme("shared", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z");
    const { fetch } = fake((batch) => ({ kind: "ok", grid: grid(batch, () => [shared]) }));

    const result = await fetchBounded({ channelIds: ["sd", "hd"], from, to }, fetch);

    expect(result.kind === "ok" && result.channels.map((row) => row.programmes.length)).toEqual(
      [1, 1],
    );
  });

  it("never has more than two requests in flight while splitting", async () => {
    const { fetch, asked, peak } = fake((batch) =>
      batch.channelIds.length > 13 ? { kind: "too-large" } : { kind: "ok", grid: grid(batch) },
    );

    const result = await fetchBounded({ channelIds: ids(100), from, to }, fetch);

    // 100 → 50 ×2 → 25 ×4 → 13 + 12, ×4: fifteen requests, never three at once.
    expect(asked).toHaveLength(15);
    expect(peak()).toBe(MAX_CONCURRENT);
    expect(result.kind === "ok" && result.channels).toHaveLength(100);
  });

  it("gives up after three levels of splitting, and says it was too large", async () => {
    const { fetch, asked } = fake(() => ({ kind: "too-large" }));

    const result = await fetchBounded({ channelIds: ids(100), from, to }, fetch);

    expect(result).toEqual({ kind: "too-large" });
    // Depth 0 (100), 1 (50), 2 (25), 3 (13 and 12): a piece at depth 3 is not
    // split again, so nothing smaller than 12 is ever asked for.
    const deepest = Math.min(...asked.map((batch) => batch.channelIds.length));
    expect(deepest).toBe(12);
    expect(MAX_SPLIT_DEPTH).toBe(3);
  });

  it("fails the whole read when one sub-request fails for another reason", async () => {
    const { fetch } = fake((batch) => {
      if (batch.channelIds.length > 5) return { kind: "too-large" };
      return batch.channelIds[0] === "c005"
        ? { kind: "failed", code: "CHANNEL_NOT_FOUND" }
        : { kind: "ok", grid: grid(batch) };
    });

    const result = await fetchBounded({ channelIds: ids(10), from, to }, fetch);

    // Not the five rows that answered: that would be the partial grid the
    // contract refuses to send, rebuilt on the client.
    expect(result).toEqual({ kind: "failed", code: "CHANNEL_NOT_FOUND" });
  });

  it("reads a fetcher that throws as a failure, without a code", async () => {
    const result = await fetchBounded({ channelIds: ["a"], from, to }, async () => {
      throw new Error("ECONNRESET");
    });

    expect(result).toEqual({ kind: "failed" });
  });

  it("is a failure for an empty list: nothing was asked, so nothing is known", async () => {
    const { fetch, asked } = fake((batch) => ({ kind: "ok", grid: grid(batch) }));

    const result = await fetchBounded({ channelIds: [], from, to }, fetch);

    expect(asked).toHaveLength(0);
    expect(result).toEqual({ kind: "failed" });
  });
});
