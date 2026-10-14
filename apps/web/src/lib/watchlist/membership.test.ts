import { describe, expect, it } from "vitest";
import type { WatchlistItem, WatchlistPage } from "@/lib/api/types";
import { findMembership } from "./membership";

const SOURCE = "0b8f1c6e-6d1f-4a57-9f55-2f3c1a7f0a01";
const WANTED = "2a1b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

function item(index: number, contentId: string | null = `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`): WatchlistItem {
  return {
    id: `10000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
    source_id: SOURCE,
    content_type: "VOD",
    content_id: contentId,
    available: contentId !== null,
    title: `Test film ${index}`,
    poster_url: null,
    added_at: "2026-10-14T08:00:00Z",
  };
}

/** A list split into pages of `size`, the way the server would serve it. */
function server(items: WatchlistItem[], size: number) {
  const asked: number[] = [];
  const totalPages = Math.ceil(items.length / size);
  const load = async (page: number): Promise<WatchlistPage> => {
    asked.push(page);
    return {
      items: items.slice(page * size, (page + 1) * size),
      page,
      size,
      total_elements: items.length,
      total_pages: totalPages,
      content_types: items.length > 0 ? ["VOD"] : [],
    };
  };
  return { load, asked };
}

describe("findMembership", () => {
  it("finds an item on the first page", async () => {
    const { load, asked } = server([item(1), item(2, WANTED)], 200);
    expect(await findMembership(WANTED, load)).toEqual({
      state: "in",
      itemId: item(2).id,
    });
    expect(asked).toEqual([0]);
  });

  it("keeps reading pages until it finds the item", async () => {
    const list = [item(1), item(2), item(3), item(4), item(5, WANTED)];
    const { load, asked } = server(list, 2);
    expect(await findMembership(WANTED, load)).toEqual({ state: "in", itemId: list[4].id });
    expect(asked).toEqual([0, 1, 2]);
  });

  it("answers out after the last page, and reads no further", async () => {
    const { load, asked } = server([item(1), item(2), item(3)], 2);
    expect(await findMembership(WANTED, load)).toEqual({ state: "out" });
    expect(asked).toEqual([0, 1]);
  });

  it("answers out for an empty list", async () => {
    const { load } = server([], 200);
    expect(await findMembership(WANTED, load)).toEqual({ state: "out" });
  });

  it("never matches an unavailable item, which has no content id", async () => {
    const { load } = server([item(1, null)], 200);
    expect(await findMembership(WANTED, load)).toEqual({ state: "out" });
  });

  it("answers unknown when any page fails, not out", async () => {
    const list = [item(1), item(2), item(3), item(4, WANTED)];
    const { load } = server(list, 2);
    const failing = async (page: number) => (page === 1 ? undefined : load(page));
    expect(await findMembership(WANTED, failing)).toEqual({ state: "unknown" });

    const throwing = async () => {
      throw new TypeError("fetch failed");
    };
    expect(await findMembership(WANTED, throwing)).toEqual({ state: "unknown" });
  });

  it("stops a walk that would never end", async () => {
    const endless = async (page: number): Promise<WatchlistPage> => ({
      items: [item(page)],
      page,
      size: 1,
      total_elements: Number.MAX_SAFE_INTEGER,
      total_pages: Number.MAX_SAFE_INTEGER,
      content_types: ["VOD"],
    });
    expect(await findMembership(WANTED, endless)).toEqual({ state: "unknown" });
  });
});
