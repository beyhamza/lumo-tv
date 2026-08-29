import { describe, expect, it } from "vitest";
import type { Channel } from "@/lib/api/types";
import { IDS_PER_REQUEST, resolveChannels } from "@/lib/catalogue/resolve-channels";

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

    const byId = await resolveChannels(refs, async (_source, batch) => {
      asked.push(batch);
      return batch.map(channel);
    });

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

    const byId = await resolveChannels(refs, async (sourceId, batch) => {
      if (sourceId === "down") throw new Error("unreachable");
      return batch.map(channel);
    });

    // One provider being unreachable is not the screen being unreachable.
    expect([...byId.keys()]).toEqual(["u1"]);
  });

  it("leaves out an id the answer does not carry, rather than a gap", async () => {
    const refs = [
      { sourceId: "a", channelId: "here" },
      { sourceId: "a", channelId: "gone" },
    ];

    const byId = await resolveChannels(refs, async (_source, batch) =>
      batch.filter((id) => id !== "gone").map(channel),
    );

    expect(byId.has("gone")).toBe(false);
    expect(byId.has("here")).toBe(true);
  });

  it("asks nothing at all when there is nothing to resolve", async () => {
    let calls = 0;

    const byId = await resolveChannels([], async () => {
      calls += 1;
      return [];
    });

    expect(calls).toBe(0);
    expect(byId.size).toBe(0);
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
