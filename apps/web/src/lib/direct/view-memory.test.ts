import { describe, expect, it } from "vitest";
import {
  DEFAULT_DIRECT_VIEW,
  DIRECT_VIEW_COOKIE_PREFIX,
  directViewCookieName,
  directViewHref,
  explicitView,
  isDirectView,
  resolveDirectView,
  storedDirectView,
} from "./view-memory";

// Bench identifiers. Nothing here names a real provider: the rules are about
// which view opens, not about what a source holds.
const SOURCE = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";

/** A `cookies().get(name)?.value` stand-in. */
const jar = (entries: Record<string, string> = {}) => (name: string) => entries[name];

/**
 * The Direct view decision (S9-04-06).
 *
 * Every branch, because each wrong answer here is silent: opening the Guide
 * when the person left on Chaînes looks like a working screen, and the memory's
 * whole job is to not be noticed.
 */
describe("isDirectView", () => {
  it("accepts exactly the two views", () => {
    expect(isDirectView("channels")).toBe(true);
    expect(isDirectView("guide")).toBe(true);
  });

  it("refuses anything else, including near-misses", () => {
    for (const value of ["Channels", "GUIDE", "grid", "", undefined, null, 1, {}]) {
      expect(isDirectView(value)).toBe(false);
    }
  });
});

describe("directViewCookieName", () => {
  it("builds one cookie per source, lower-cased", () => {
    expect(directViewCookieName(SOURCE.toUpperCase())).toBe(
      `${DIRECT_VIEW_COOKIE_PREFIX}${SOURCE}`,
    );
  });

  it("refuses an id that is not a UUID rather than building a name from it", () => {
    expect(directViewCookieName("../../etc/passwd")).toBeNull();
    expect(directViewCookieName("")).toBeNull();
  });
});

describe("storedDirectView", () => {
  it("reads a stored view for this source", () => {
    expect(
      storedDirectView(jar({ [`${DIRECT_VIEW_COOKIE_PREFIX}${SOURCE}`]: "guide" }), SOURCE),
    ).toBe("guide");
  });

  it("reads nothing when another source's cookie is the only one", () => {
    expect(
      storedDirectView(jar({ [`${DIRECT_VIEW_COOKIE_PREFIX}${OTHER}`]: "guide" }), SOURCE),
    ).toBeNull();
  });

  it("treats a hand-edited value as no cookie at all", () => {
    expect(
      storedDirectView(jar({ [`${DIRECT_VIEW_COOKIE_PREFIX}${SOURCE}`]: "grid" }), SOURCE),
    ).toBeNull();
  });

  it("never reads for an id that is not a UUID", () => {
    expect(storedDirectView(jar({ lumo_direct_view_other: "guide" }), "..")).toBeNull();
  });
});

describe("explicitView", () => {
  it("accepts a string and the first of an array", () => {
    expect(explicitView("guide")).toBe("guide");
    expect(explicitView(["guide", "channels"])).toBe("guide");
  });

  it("ignores an unknown value, an empty one and an absence", () => {
    expect(explicitView("grid")).toBeUndefined();
    expect(explicitView("")).toBeUndefined();
    expect(explicitView(undefined)).toBeUndefined();
    expect(explicitView([])).toBeUndefined();
  });
});

describe("resolveDirectView", () => {
  it("lets the explicit view prime the memory", () => {
    expect(resolveDirectView("channels", "guide")).toBe("guide");
    expect(resolveDirectView("guide", "channels")).toBe("channels");
  });

  it("respects the memory when nothing is explicit (GD-02)", () => {
    expect(resolveDirectView("guide", undefined)).toBe("guide");
  });

  it("opens Chaînes on a first visit, and for an old S8 link", () => {
    expect(resolveDirectView(null, undefined)).toBe(DEFAULT_DIRECT_VIEW);
  });
});

describe("directViewHref", () => {
  it("always carries the view, and nothing else when there is nothing else", () => {
    expect(directViewHref("guide", { sourceId: SOURCE })).toBe(
      `/app/sources/${SOURCE}/channels?view=guide`,
    );
  });

  it("keeps the filter, the search, the page and the player (GD-01)", () => {
    const href = directViewHref("guide", {
      sourceId: SOURCE,
      categoryId: "sport",
      q: "aîne 0",
      page: 3,
      group: OTHER,
      play: SOURCE,
    });
    const query = new URLSearchParams(href.split("?")[1]);

    expect(query.get("view")).toBe("guide");
    expect(query.get("categoryId")).toBe("sport");
    expect(query.get("q")).toBe("aîne 0");
    expect(query.get("page")).toBe("3");
    expect(query.get("group")).toBe(OTHER);
    expect(query.get("play")).toBe(SOURCE);
  });

  it("omits an absent or empty value rather than sending it empty", () => {
    const href = directViewHref("channels", { sourceId: SOURCE, q: "", page: 0 });

    expect(href).toBe(`/app/sources/${SOURCE}/channels?view=channels`);
  });
});
