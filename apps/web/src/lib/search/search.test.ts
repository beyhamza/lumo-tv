import { describe, expect, it, vi } from "vitest";
import type { Channel, VodItem } from "@/lib/api/types";
import {
  codePointLength,
  filterFromParam,
  normalizeQuery,
  PAGE_SIZE,
  pageFor,
  pageSizeFor,
  PREVIEW_SIZE,
  queryTooLong,
  runSection,
  searchSections,
  type CataloguePresent,
  type SearchFetchers,
} from "./search";

/**
 * The search's rules, without a server or a screen (US-021, S10-02).
 *
 * What is pinned here is the part of Q9 a review cannot see and a screen would
 * hide: which types each filter asks, at which size and page, that the preview
 * of four and the pages of twenty are distinct requests, and that one section
 * failing leaves the other two. The debounce, the context identity and the
 * source/account changes are the phone's, proven in S10-01; the web re-reads
 * its URL on every submit, so it has no debounce of its own.
 */

const ALL_PRESENT: CataloguePresent = { channels: true, films: true, series: true };

type Call = [page: number, size: number];

/** Fetchers that record what they were asked, and answer empty `ok` pages. */
function recording(overrides: Partial<SearchFetchers> = {}): {
  fetchers: SearchFetchers;
  calls: { channels: Call[]; films: Call[]; series: Call[] };
} {
  const calls = { channels: [] as Call[], films: [] as Call[], series: [] as Call[] };

  const empty = (log: Call[]) => async (page: number, size: number) => {
    log.push([page, size]);
    return { ok: true as const, items: [], totalElements: 0 };
  };

  return {
    fetchers: {
      channels: overrides.channels ?? empty(calls.channels),
      films: overrides.films ?? empty(calls.films),
      series: overrides.series ?? empty(calls.series),
    },
    calls,
  };
}

describe("search query rules", () => {
  it("trims before building the request", () => {
    expect(normalizeQuery("  falaise  ")).toBe("falaise");
    expect(normalizeQuery(undefined)).toBe("");
  });

  it("counts code points, so an astral character is one", () => {
    expect(codePointLength("🙂")).toBe(1);
    expect(codePointLength("a🙂b")).toBe(3);
  });

  it("refuses past a hundred code points, never truncates", () => {
    expect(queryTooLong("a".repeat(100))).toBe(false);
    expect(queryTooLong("a".repeat(101))).toBe(true);
    // A hundred emoji are a hundred characters, not two hundred UTF-16 units.
    expect(queryTooLong("🙂".repeat(100))).toBe(false);
    expect(queryTooLong("🙂".repeat(101))).toBe(true);
  });

  it("falls back to the grouped view for an unknown filter", () => {
    expect(filterFromParam("films")).toBe("films");
    expect(filterFromParam("series")).toBe("series");
    expect(filterFromParam("channels")).toBe("channels");
    expect(filterFromParam("nonsense")).toBe("all");
    expect(filterFromParam(undefined)).toBe("all");
  });
});

describe("distinct preview and page requests", () => {
  it("asks four on the grouped view and twenty on a list", () => {
    expect(pageSizeFor("all")).toBe(PREVIEW_SIZE);
    expect(pageSizeFor("films")).toBe(PAGE_SIZE);
  });

  it("always shows page zero on the grouped view, and honours a list's page", () => {
    expect(pageFor("all", 7)).toBe(0);
    expect(pageFor("films", 7)).toBe(7);
    expect(pageFor("films", -3)).toBe(0);
  });
});

describe("searchSections", () => {
  it("asks the three types together, at the given size", async () => {
    const { fetchers, calls } = recording();
    const sections = await searchSections("all", 0, PREVIEW_SIZE, fetchers, ALL_PRESENT);

    expect(calls.channels).toEqual([[0, PREVIEW_SIZE]]);
    expect(calls.films).toEqual([[0, PREVIEW_SIZE]]);
    expect(calls.series).toEqual([[0, PREVIEW_SIZE]]);
    expect(sections.channels?.status).toBe("ok");
    expect(sections.films?.status).toBe("ok");
    expect(sections.series?.status).toBe("ok");
  });

  it("asks only the selected type, at the page size", async () => {
    const { fetchers, calls } = recording();
    const sections = await searchSections("films", 0, PAGE_SIZE, fetchers, ALL_PRESENT);

    expect(calls.channels).toEqual([]);
    expect(calls.series).toEqual([]);
    expect(calls.films).toEqual([[0, PAGE_SIZE]]);
    // The type the filter did not ask for is null, not failed and not empty.
    expect(sections.channels).toBeNull();
    expect(sections.series).toBeNull();
    expect(sections.films?.status).toBe("ok");
  });

  it("does not ask a type the source does not carry", async () => {
    const { fetchers, calls } = recording();
    const sections = await searchSections("all", 0, PREVIEW_SIZE, fetchers, {
      channels: true,
      films: false,
      series: false,
    });

    expect(calls.channels).toEqual([[0, PREVIEW_SIZE]]);
    expect(calls.films).toEqual([]);
    expect(calls.series).toEqual([]);
    expect(sections.films).toBeNull();
    expect(sections.series).toBeNull();
  });

  it("keeps the other sections when one fails", async () => {
    const { fetchers } = recording({
      films: async () => ({ ok: false as const, code: "INTERNAL_ERROR" }),
    });
    const sections = await searchSections("all", 0, PREVIEW_SIZE, fetchers, ALL_PRESENT);

    expect(sections.channels?.status).toBe("ok");
    expect(sections.series?.status).toBe("ok");
    expect(sections.films).toEqual({ status: "failed", code: "INTERNAL_ERROR" });
  });

  it("treats a rejection as a failure, not as an empty section", async () => {
    const { fetchers } = recording({
      channels: async () => {
        throw new Error("no answer");
      },
    });
    const sections = await searchSections("all", 0, PREVIEW_SIZE, fetchers, ALL_PRESENT);

    expect(sections.channels).toEqual({ status: "failed" });
    expect(sections.channels?.status).not.toBe("ok");
  });
});

describe("runSection", () => {
  it("returns null for a type the filter skipped, without calling it", async () => {
    const fetch = vi.fn(async () => ({
      ok: true as const,
      items: [] as Channel[],
      totalElements: 0,
    }));
    await expect(runSection(false, fetch)).resolves.toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("carries the total so Voir tous can be offered", async () => {
    const items = [{ id: "one" } as VodItem];
    const fetch = async () => ({ ok: true as const, items, totalElements: 42 });
    await expect(runSection(true, fetch)).resolves.toEqual({
      status: "ok",
      items,
      totalElements: 42,
    });
  });
});
