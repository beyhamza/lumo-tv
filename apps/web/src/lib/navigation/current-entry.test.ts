import { describe, expect, it } from "vitest";
import { currentEntryIndex } from "./current-entry";

const ACTIVE = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";

// The rail as the layout builds it for a selected source, in its order.
const ENTRIES = [
  { href: "/fr/app", exact: true },
  { href: `/fr/app/sources/${ACTIVE}/channels` },
  { href: `/fr/app/sources/${ACTIVE}/vod` },
  { href: `/fr/app/sources/${ACTIVE}/series` },
  { href: "/fr/app/favorites" },
  { href: "/fr/app/sources" },
  { href: "/fr/app/settings" },
];
const [HOME, LIVE, FILMS, SERIES, LIBRARY, SOURCES, SETTINGS] = ENTRIES.map(
  (_, index) => index,
);

/**
 * One current entry in the rail, never two (US-017, S8-E03).
 *
 * `aria-current` is announced: two of them is a screen reader saying "current
 * page" twice, and none on a page that has an entry is a menu that lost its
 * place.
 */
describe("currentEntryIndex", () => {
  it("marks Home on the home page", () => {
    expect(currentEntryIndex("/fr/app", ENTRIES)).toBe(HOME);
  });

  it("marks Home nowhere else, although every page is under /app", () => {
    expect(currentEntryIndex("/fr/app/settings", ENTRIES)).toBe(SETTINGS);
    expect(currentEntryIndex("/fr/app/settings/account", ENTRIES)).toBe(SETTINGS);
    expect(currentEntryIndex("/fr/app/somewhere-new", ENTRIES)).toBe(-1);
  });

  it("marks the catalogue of the active source, not Sources above it", () => {
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/channels`, ENTRIES)).toBe(LIVE);
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/vod`, ENTRIES)).toBe(FILMS);
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/series`, ENTRIES)).toBe(SERIES);
  });

  it("keeps the catalogue marked on a film or a series page", () => {
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/vod/film-1`, ENTRIES)).toBe(FILMS);
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/series/series-1`, ENTRIES)).toBe(SERIES);
  });

  it("marks Sources on another source's catalogue, reached from My sources", () => {
    expect(currentEntryIndex(`/fr/app/sources/${OTHER}/vod`, ENTRIES)).toBe(SOURCES);
  });

  it("marks Sources on the list, a source's page and the add form", () => {
    expect(currentEntryIndex("/fr/app/sources", ENTRIES)).toBe(SOURCES);
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}`, ENTRIES)).toBe(SOURCES);
    expect(currentEntryIndex("/fr/app/sources/new", ENTRIES)).toBe(SOURCES);
  });

  it("marks My library on the favourites page", () => {
    expect(currentEntryIndex("/fr/app/favorites", ENTRIES)).toBe(LIBRARY);
  });

  it("matches whole segments only", () => {
    expect(currentEntryIndex("/fr/app/sources-archive", ENTRIES)).toBe(-1);
  });

  it("does not match across locales", () => {
    expect(currentEntryIndex("/en/app/settings", ENTRIES)).toBe(-1);
  });

  it("still marks one entry when no source is selected", () => {
    // The three catalogue entries are absent from the rail in that state.
    const withoutCatalogues = ENTRIES.filter((entry) => !entry.href.includes(ACTIVE));

    expect(currentEntryIndex("/fr/app", withoutCatalogues)).toBe(0);
    expect(currentEntryIndex(`/fr/app/sources/${ACTIVE}/vod`, withoutCatalogues)).toBe(
      withoutCatalogues.findIndex((entry) => entry.href === "/fr/app/sources"),
    );
  });
});
