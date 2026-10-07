import { describe, expect, it, vi } from "vitest";
import type { Channel, VodItem } from "@/lib/api/types";
import {
  codePointLength,
  filterFromParam,
  formatPageOf,
  isOffline,
  normalizeQuery,
  openedFromSearch,
  PAGE_SIZE,
  pageFor,
  pageSizeFor,
  PREVIEW_SIZE,
  queryTooLong,
  requestedTypes,
  runSection,
  searchPath,
  searchResultPath,
  searchReturnPath,
  searchSections,
  searchVerdict,
  SEARCH_ORIGIN,
  type CataloguePresent,
  type SearchFetchers,
  type SearchSections,
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

describe("opening a result and coming back", () => {
  it("starts a channel on its source's page, without a search context", () => {
    // A channel has nothing to say about itself: `?play=` is the whole gesture.
    expect(
      searchResultPath("channels", "chan-1", "src-9", {
        query: "falaise",
        filter: "channels",
        page: 2,
      }),
    ).toBe("/app/sources/src-9/channels?play=chan-1");
  });

  it("opens a film on its own page, with the search it must return to", () => {
    const path = searchResultPath("films", "film-3", "src-9", {
      query: "voyage",
      filter: "films",
      page: 1,
    });
    expect(path).toBe(
      "/app/sources/src-9/vod/film-3?from=search&q=voyage&type=films&page=1",
    );
  });

  it("opens a series on its own page, keeping the grouped view and page zero out", () => {
    const path = searchResultPath("series", "ser-7", "src-9", {
      query: "falaises",
      filter: "all",
      page: 0,
    });
    expect(path).toBe("/app/sources/src-9/series/ser-7?from=search&q=falaises");
  });

  it("rebuilds the return path from the search screen and its three parameters only", () => {
    expect(searchReturnPath({ q: "voyage", type: "films", page: "2" })).toBe(
      "/app/search?q=voyage&type=films&page=2",
    );
    // Nothing that could have been put in the query becomes a path or a host.
    expect(searchReturnPath({ q: "x" })).toBe("/app/search?q=x");
    expect(searchReturnPath({})).toBe("/app/search");
  });

  it("honours the exact provenance marker and nothing else", () => {
    expect(openedFromSearch(SEARCH_ORIGIN)).toBe(true);
    expect(openedFromSearch("Search")).toBe(false);
    expect(openedFromSearch("//evil.example/app/search")).toBe(false);
    expect(openedFromSearch(undefined)).toBe(false);
  });
});

describe("the four states of a search (S10-04)", () => {
  /** A source's catalogue, with only the named types present. */
  function sourceCarrying(...types: (keyof CataloguePresent)[]): CataloguePresent {
    return {
      channels: types.includes("channels"),
      films: types.includes("films"),
      series: types.includes("series"),
    };
  }

  it("requests only the types the filter and the source allow", () => {
    expect(requestedTypes("all", ALL_PRESENT)).toEqual(["channels", "films", "series"]);
    expect(requestedTypes("films", ALL_PRESENT)).toEqual(["films"]);
    expect(requestedTypes("all", sourceCarrying("channels", "series"))).toEqual([
      "channels",
      "series",
    ]);
    // A type the source does not carry is never requested, even if open.
    expect(requestedTypes("films", sourceCarrying("channels"))).toEqual([]);
  });

  it("calls a search with no matches empty only when every section answered", () => {
    const sections: SearchSections = {
      channels: { status: "ok", items: [], totalElements: 0 },
      films: { status: "ok", items: [], totalElements: 0 },
      series: { status: "ok", items: [], totalElements: 0 },
    };
    const verdict = searchVerdict(sections, "all", ALL_PRESENT);
    expect(verdict.noResults).toBe(true);
    expect(verdict.allFailed).toBe(false);
    expect(verdict.total).toBe(0);
  });

  it("does not call a partial failure empty, and names the failed section", () => {
    const sections: SearchSections = {
      channels: { status: "ok", items: [{ id: "c1" } as Channel], totalElements: 1 },
      films: { status: "failed", code: "INTERNAL_ERROR" },
      series: { status: "failed" },
    };
    const verdict = searchVerdict(sections, "all", ALL_PRESENT);
    expect(verdict.noResults).toBe(false);
    expect(verdict.failed).toEqual(["films", "series"]);
    expect(verdict.anyOk).toBe(true);
    expect(verdict.allFailed).toBe(false);
    expect(verdict.total).toBe(1);
  });

  it("calls a search where every section failed a total failure, not an empty set", () => {
    const sections: SearchSections = {
      channels: { status: "failed" },
      films: { status: "failed" },
      series: { status: "failed" },
    };
    const verdict = searchVerdict(sections, "all", ALL_PRESENT);
    expect(verdict.allFailed).toBe(true);
    expect(verdict.noResults).toBe(false);
    expect(verdict.anyOk).toBe(false);
  });

  it("judges only the type a list filter opened", () => {
    const sections: SearchSections = {
      channels: null,
      films: { status: "ok", items: [], totalElements: 0 },
      series: null,
    };
    const verdict = searchVerdict(sections, "films", ALL_PRESENT);
    expect(verdict.requested).toEqual(["films"]);
    expect(verdict.noResults).toBe(true);
  });
});

describe("searchPath", () => {
  it("omits the grouped view and page zero, keeps a real filter and page", () => {
    expect(searchPath({ q: "café" })).toBe("/app/search?q=caf%C3%A9");
    expect(searchPath({ q: "café", type: "all", page: 0 })).toBe("/app/search?q=caf%C3%A9");
    expect(searchPath({ q: "café", type: "films", page: 2 })).toBe(
      "/app/search?q=caf%C3%A9&type=films&page=2",
    );
  });
});

describe("formatPageOf", () => {
  it("fills both placeholders and leaves no braces", () => {
    expect(formatPageOf("Page {page} sur {total}", 2, 5)).toBe("Page 2 sur 5");
    expect(formatPageOf("Page {page} of {total}", 1, 1)).toBe("Page 1 of 1");
  });
});

describe("isOffline", () => {
  it("is true only when the browser says it is offline", () => {
    expect(isOffline({ onLine: false })).toBe(true);
    expect(isOffline({ onLine: true })).toBe(false);
    expect(isOffline(undefined)).toBe(false);
  });
});
