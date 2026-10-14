import { describe, expect, it } from "vitest";
import type { WatchlistPage } from "@/lib/api/types";
import {
  contentTypeOf,
  filterChoices,
  filterFromParam,
  pageFromParam,
  watchlistView,
} from "./list";

const SOURCE = "0b8f1c6e-6d1f-4a57-9f55-2f3c1a7f0a01";

function page(overrides: Partial<WatchlistPage>): WatchlistPage {
  return {
    items: [],
    page: 0,
    size: 48,
    total_elements: 0,
    total_pages: 0,
    content_types: [],
    ...overrides,
  };
}

const ITEM = {
  id: "1f0e6a3c-2b7d-4e8f-9a01-23456789abcd",
  source_id: SOURCE,
  content_type: "VOD" as const,
  content_id: "2a1b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  available: true,
  title: "Big Buck Bunny",
  poster_url: null,
  added_at: "2026-10-14T08:00:00Z",
};

describe("filterChoices (WL-17)", () => {
  it("shows no selector for an empty list", () => {
    expect(filterChoices([])).toEqual([]);
  });

  it("shows no selector when only one type is present", () => {
    expect(filterChoices(["VOD"])).toEqual([]);
    expect(filterChoices(["SERIES"])).toEqual([]);
  });

  it("does not count a type twice", () => {
    expect(filterChoices(["VOD", "VOD"])).toEqual([]);
  });

  it("offers All, Films, Series once both types are present", () => {
    expect(filterChoices(["SERIES", "VOD"])).toEqual(["all", "films", "series"]);
  });
});

describe("filterFromParam / contentTypeOf", () => {
  it("reads the two filters and falls back to all", () => {
    expect(filterFromParam("films")).toBe("films");
    expect(filterFromParam("series")).toBe("series");
    expect(filterFromParam(undefined)).toBe("all");
    expect(filterFromParam("VOD")).toBe("all");
    expect(filterFromParam("channels")).toBe("all");
  });

  it("maps a filter to the contract's content type", () => {
    expect(contentTypeOf("films")).toBe("VOD");
    expect(contentTypeOf("series")).toBe("SERIES");
    expect(contentTypeOf("all")).toBeUndefined();
  });
});

describe("pageFromParam", () => {
  it("is zero-based and refuses garbage", () => {
    expect(pageFromParam(undefined)).toBe(0);
    expect(pageFromParam("2")).toBe(2);
    expect(pageFromParam("-3")).toBe(0);
    expect(pageFromParam("abc")).toBe(0);
  });
});

describe("watchlistView", () => {
  it("draws items when there are some", () => {
    expect(
      watchlistView(
        page({ items: [ITEM], total_elements: 1, total_pages: 1, content_types: ["VOD"] }),
        "all",
      ),
    ).toBe("items");
  });

  it("says the list is empty only when the source's list holds nothing", () => {
    expect(watchlistView(page({}), "all")).toBe("empty");
    expect(watchlistView(page({}), "series")).toBe("empty");
  });

  it("never calls a filter without result an empty list", () => {
    expect(watchlistView(page({ content_types: ["VOD"] }), "series")).toBe("filtered-empty");
  });

  it("recognises a page past the end", () => {
    expect(
      watchlistView(
        page({ page: 4, total_elements: 3, total_pages: 1, content_types: ["VOD"] }),
        "all",
      ),
    ).toBe("out-of-range");
  });
});
