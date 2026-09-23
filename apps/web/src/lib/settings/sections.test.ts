import { describe, expect, it } from "vitest";
import { currentEntryIndex } from "@/lib/navigation/current-entry";
import {
  DEFAULT_SETTINGS_SECTION,
  SETTINGS_SECTIONS,
  resolveSettingsSection,
  settingsSectionPath,
} from "./sections";

/**
 * Which URL opens which section of Settings (US-025, S8-06).
 *
 * The segment comes from the URL, so it is untrusted: anything that is not one
 * of the sections must resolve to nothing, and the page turns nothing into a
 * 404 rather than into the first section.
 */
describe("resolveSettingsSection", () => {
  it("accepts every section, by its exact segment", () => {
    for (const section of SETTINGS_SECTIONS) {
      expect(resolveSettingsSection(section)).toBe(section);
    }
  });

  it("has a default that is one of the sections", () => {
    expect(SETTINGS_SECTIONS).toContain(DEFAULT_SETTINGS_SECTION);
  });

  it("refuses a section this application does not have", () => {
    // The two retired screens are the interesting ones: a bookmark to either
    // must not open silently on the account section.
    expect(resolveSettingsSection("subscription")).toBeNull();
    expect(resolveSettingsSection("billing")).toBeNull();
    // "Playback" arrives in sprint 13 and is not a section until then.
    expect(resolveSettingsSection("playback")).toBeNull();
    // Sources are a link in the list, not a section: their page is /app/sources.
    expect(resolveSettingsSection("sources")).toBeNull();
  });

  it("does not fall for a near miss", () => {
    expect(resolveSettingsSection("Account")).toBeNull();
    expect(resolveSettingsSection("account/")).toBeNull();
    expect(resolveSettingsSection("accounts")).toBeNull();
    expect(resolveSettingsSection("")).toBeNull();
  });

  it("refuses anything that is not a string", () => {
    expect(resolveSettingsSection(["account"])).toBeNull();
    expect(resolveSettingsSection(undefined)).toBeNull();
    expect(resolveSettingsSection(null)).toBeNull();
  });
});

/**
 * The account rail marks Settings as current on every section — once, and
 * without knowing the sections: the layout lists `/app/settings` and the
 * most-specific-prefix rule does the rest (`currentEntryIndex`).
 */
describe("the Settings entry of the rail", () => {
  // The rail as the layout builds it for a selected source, after S8-06:
  // Home, the three catalogues, My library, then Sources and Settings.
  const ACTIVE = "11111111-1111-4111-8111-111111111111";
  const ENTRIES = [
    { href: "/fr/app", exact: true },
    { href: `/fr/app/sources/${ACTIVE}/channels` },
    { href: `/fr/app/sources/${ACTIVE}/vod` },
    { href: `/fr/app/sources/${ACTIVE}/series` },
    { href: "/fr/app/favorites" },
    { href: "/fr/app/sources" },
    { href: "/fr/app/settings" },
  ];
  const SETTINGS = ENTRIES.length - 1;
  const SOURCES = ENTRIES.length - 2;

  it("is current on /app/settings and on every section", () => {
    expect(currentEntryIndex("/fr/app/settings", ENTRIES)).toBe(SETTINGS);
    for (const section of SETTINGS_SECTIONS) {
      expect(currentEntryIndex(`/fr${settingsSectionPath(section)}`, ENTRIES)).toBe(SETTINGS);
    }
  });

  it("is current alone: a section with the confirmation open still marks one entry", () => {
    // The pathname the proxy reports carries no query string, so
    // `?confirm=signout` changes nothing here — asserted so that it stays true.
    const marked = ENTRIES.filter(
      (_, index) => index === currentEntryIndex("/fr/app/settings/account", ENTRIES),
    );
    expect(marked).toHaveLength(1);
    expect(marked[0].href).toBe("/fr/app/settings");
  });

  it("leaves Sources to the sources page, which the list links to", () => {
    expect(currentEntryIndex("/fr/app/sources", ENTRIES)).toBe(SOURCES);
  });

  it("marks nothing on the retired routes, which redirect or 404", () => {
    expect(currentEntryIndex("/fr/app/devices", ENTRIES)).toBe(-1);
    expect(currentEntryIndex("/fr/app/subscription", ENTRIES)).toBe(-1);
  });
});
