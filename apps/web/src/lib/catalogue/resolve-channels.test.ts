import { describe, expect, it } from "vitest";
import type { Channel } from "@/lib/api/types";
import {
  IDS_PER_REQUEST,
  channelStatus,
  resolveChannels,
} from "@/lib/catalogue/resolve-channels";

/**
 * Resolving favourites across sources (S6-09).
 *
 * <h2>Why this has a test when most of the screen does not</h2>
 *
 * The screen itself is a list and some links. What can be wrong is the batching,
 * and it is wrong in the way this codebase keeps calling the most expensive: a
 * `200`, no error, and a group of a hundred and twenty channels showing a hundred
 * tout rond. Nobody reports that — they conclude their favourites are wrong and
 * stop trusting the screen.
 *
 * The phone paid this bill once (`S4-02`, case `R-140`). Paying it twice would be
 * a choice.
 */
describe("resolveChannels", () => {
  it("splits one source's ids into batches of the contract's ceiling", async () => {
    const asked: string[][] = [];
    const refs = ids(120).map((channelId) => ({ sourceId: "a", channelId }));

    const { byId, failed } = await resolveChannels(refs, async (_source, batch) => {
      asked.push(batch);
      return batch.map(channel);
    });

    expect(failed.size).toBe(0);
    // Two requests, not one truncated to a hundred. A `.slice(0, 100)` here
    // would answer `200` and lose twenty channels without a word.
    expect(asked.map((batch) => batch.length)).toEqual([100, 20]);
    expect(byId.size).toBe(120);
  });

  it("asks each source separately", async () => {
    const asked: string[] = [];
    const refs = [
      { sourceId: "a", channelId: "a1" },
      { sourceId: "b", channelId: "b1" },
      { sourceId: "a", channelId: "a2" },
    ];

    await resolveChannels(refs, async (sourceId, batch) => {
      asked.push(sourceId);
      return batch.map(channel);
    });

    // `GET /sources/{id}/channels` is one operation per source; there is no
    // account-wide equivalent, which is the whole reason this module exists.
    expect(asked.toSorted()).toEqual(["a", "b"]);
  });

  it("batches per source rather than across them", async () => {
    const asked: { sourceId: string; size: number }[] = [];
    const refs = [
      ...ids(60).map((channelId) => ({ sourceId: "a", channelId: `a-${channelId}` })),
      ...ids(60).map((channelId) => ({ sourceId: "b", channelId: `b-${channelId}` })),
    ];

    await resolveChannels(refs, async (sourceId, batch) => {
      asked.push({ sourceId, size: batch.length });
      return batch.map(channel);
    });

    // A hundred and twenty ids over two sources is two requests of sixty, not
    // one of a hundred plus one of twenty: the ceiling is per request and the
    // operation is per source, so a batch can never span two.
    expect(asked).toHaveLength(2);
    expect(asked.every((request) => request.size === 60)).toBe(true);
  });

  it("sends `size` with every batch", async () => {
    const sizes: number[] = [];
    const refs = ids(150).map((channelId) => ({ sourceId: "a", channelId }));

    await resolveChannels(refs, async (_source, batch, size) => {
      sizes.push(size);
      return batch.map(channel);
    });

    // `size` defaults to 50. Without it a batch of a hundred comes back half
    // answered, with a `200` and nothing to notice.
    expect(sizes).toEqual([IDS_PER_REQUEST, IDS_PER_REQUEST]);
  });

  it("asks for a channel once even when it is starred into two groups", async () => {
    const asked: string[][] = [];
    const refs = [
      { sourceId: "a", channelId: "same" },
      { sourceId: "a", channelId: "same" },
      { sourceId: "a", channelId: "other" },
    ];

    await resolveChannels(refs, async (_source, batch) => {
      asked.push(batch);
      return batch.map(channel);
    });

    expect(asked).toEqual([["same", "other"]]);
  });

  it("keeps the other sources when one fails", async () => {
    const refs = [
      { sourceId: "up", channelId: "u1" },
      { sourceId: "down", channelId: "d1" },
    ];

    const { byId, failed } = await resolveChannels(refs, async (sourceId, batch) => {
      if (sourceId === "down") throw new Error("unreachable");
      return batch.map(channel);
    });

    // One provider being unreachable is not the screen being unreachable.
    expect([...byId.keys()]).toEqual(["u1"]);
    // And its channels are reported as unknown, not silently dropped (S11-05).
    expect([...failed]).toEqual(["d1"]);
  });

  it("reports every id of a failed batch, and only those", async () => {
    // 150 ids: batch one (c0…c99) answers, batch two (c100…c149) fails.
    const refs = ids(150).map((channelId) => ({ sourceId: "a", channelId }));
    let call = 0;

    const resolution = await resolveChannels(refs, async (_source, batch) => {
      call += 1;
      if (call === 2) throw new Error("timeout");
      return batch.map(channel);
    });

    expect(resolution.byId.size).toBe(100);
    expect(resolution.failed.size).toBe(50);
    expect(resolution.failed.has("c100")).toBe(true);
    expect(resolution.failed.has("c99")).toBe(false);
  });

  it("leaves out an id the answer does not carry, rather than a gap", async () => {
    const refs = [
      { sourceId: "a", channelId: "here" },
      { sourceId: "a", channelId: "gone" },
    ];

    const resolution = await resolveChannels(refs, async (_source, batch) =>
      batch.filter((id) => id !== "gone").map(channel),
    );

    expect(resolution.byId.has("gone")).toBe(false);
    expect(resolution.byId.has("here")).toBe(true);
    // Answered and absent is "gone", not "unavailable": a retry will not bring it back.
    expect(resolution.failed.has("gone")).toBe(false);
  });

  it("asks nothing at all when there is nothing to resolve", async () => {
    let calls = 0;

    const { byId, failed } = await resolveChannels([], async () => {
      calls += 1;
      return [];
    });

    expect(calls).toBe(0);
    expect(byId.size).toBe(0);
    expect(failed.size).toBe(0);
  });
});

describe("channelStatus", () => {
  it("tells a resolved channel, an unknown one and a gone one apart", async () => {
    const refs = [
      { sourceId: "up", channelId: "here" },
      { sourceId: "up", channelId: "gone" },
      { sourceId: "down", channelId: "unknown" },
    ];
    const resolution = await resolveChannels(refs, async (sourceId, batch) => {
      if (sourceId === "down") throw new Error("503");
      return batch.filter((id) => id !== "gone").map(channel);
    });

    expect(channelStatus(resolution, "here")).toEqual({
      state: "resolved",
      channel: channel("here"),
    });
    // The batch failed: nothing is known, and in particular not that it is gone.
    expect(channelStatus(resolution, "unknown")).toEqual({ state: "unavailable" });
    // The batch answered without it: it left the catalogue.
    expect(channelStatus(resolution, "gone")).toEqual({ state: "gone" });
  });
});

function ids(count: number): string[] {
  return Array.from({ length: count }, (_, index) => `c${index}`);
}

function channel(id: string): Channel {
  return {
    id,
    source_id: "a",
    name: id,
    number: null,
    logo_url: null,
    category_id: null,
    is_adult: false,
  } as unknown as Channel;
}
