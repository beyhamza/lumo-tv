import { describe, expect, it } from "vitest";
import { sourceSwitchTarget } from "./switch-target";

const OLD = "11111111-1111-4111-8111-111111111111";
const NEW = "22222222-2222-4222-8222-222222222222";
const ITEM = "33333333-3333-4333-8333-333333333333";

const to = (from: unknown, locale = "fr") => sourceSwitchTarget(from, locale, NEW);

/**
 * Where a source switch lands (US-018).
 *
 * Two families of cases: the product rule — keep the section, never keep a page
 * or a filter of the old source — and the security rule, since the input is a
 * form field and the output is a redirect.
 */
describe("sourceSwitchTarget", () => {
  describe("keeps the open section", () => {
    it("goes from one source's channels to the other's", () => {
      expect(to(`/fr/app/sources/${OLD}/channels`)).toBe(`/app/sources/${NEW}/channels`);
    });

    it("goes from films to films and from series to series", () => {
      expect(to(`/fr/app/sources/${OLD}/vod`)).toBe(`/app/sources/${NEW}/vod`);
      expect(to(`/fr/app/sources/${OLD}/series`)).toBe(`/app/sources/${NEW}/series`);
    });

    it("works the same in the other locale", () => {
      expect(to(`/en/app/sources/${OLD}/vod`, "en")).toBe(`/app/sources/${NEW}/vod`);
    });
  });

  describe("never keeps what belonged to the old source", () => {
    it("collapses a film page to the film catalogue", () => {
      expect(to(`/fr/app/sources/${OLD}/vod/${ITEM}`)).toBe(`/app/sources/${NEW}/vod`);
    });

    it("collapses a series page to the series catalogue", () => {
      expect(to(`/fr/app/sources/${OLD}/series/${ITEM}`)).toBe(`/app/sources/${NEW}/series`);
    });

    it("collapses a detail id of any shape, since it is dropped either way", () => {
      expect(to(`/fr/app/sources/${OLD}/vod/48213`)).toBe(`/app/sources/${NEW}/vod`);
    });

    it("drops the filters, the playing channel and the page number", () => {
      expect(to(`/fr/app/sources/${OLD}/channels?category=${ITEM}&q=sport&page=7`)).toBe(
        `/app/sources/${NEW}/channels`,
      );
      expect(to(`/fr/app/sources/${OLD}/channels?play=${ITEM}`)).toBe(
        `/app/sources/${NEW}/channels`,
      );
      expect(to(`/fr/app/sources/${OLD}/vod#top`)).toBe(`/app/sources/${NEW}/vod`);
    });
  });

  describe("stays put on a page that is not a catalogue", () => {
    it.each([
      ["/fr/app", "/app"],
      ["/fr/app/sources", "/app/sources"],
      ["/fr/app/sources/new", "/app/sources/new"],
      ["/fr/app/favorites", "/app/favorites"],
      ["/fr/app/devices", "/app/devices"],
      ["/fr/app/subscription", "/app/subscription"],
    ])("%s", (from, expected) => {
      expect(to(from)).toBe(expected);
    });

    it("drops the query there too", () => {
      expect(to(`/fr/app/favorites?group=${ITEM}`)).toBe("/app/favorites");
      expect(to(`/fr/app/sources/${OLD}?confirm=delete`)).toBe(`/app/sources/${OLD}`);
    });

    it("leaves a source's own management page on that source", () => {
      // My sources manages a source whichever one is being browsed.
      expect(to(`/fr/app/sources/${OLD}`)).toBe(`/app/sources/${OLD}`);
    });
  });

  describe("refuses everything it does not recognise", () => {
    it("falls back for a path of another locale", () => {
      // The action redirects within the locale it was posted to.
      expect(to(`/en/app/sources/${OLD}/vod`, "fr")).toBe("/app");
      expect(to("/en/app/favorites", "fr")).toBe("/app");
    });

    it("falls back for a path with no locale at all", () => {
      expect(to(`/app/sources/${OLD}/vod`)).toBe("/app");
      expect(to("/app/favorites")).toBe("/app");
    });

    it("falls back for a locale that is only a prefix of the segment", () => {
      expect(to("/frais/app/favorites")).toBe("/app");
    });

    it("falls back for another site", () => {
      expect(to("https://evil.example/fr/app/favorites")).toBe("/app");
      expect(to("//evil.example/fr/app/favorites")).toBe("/app");
      expect(to("/fr//evil.example")).toBe("/app");
      expect(to("/\\evil.example")).toBe("/app");
      expect(to("/fr/\\evil.example")).toBe("/app");
    });

    it("falls back for a path outside the account zone", () => {
      expect(to("/fr/login")).toBe("/app");
      expect(to("/fr/activate")).toBe("/app");
      expect(to("/fr/guides/anything")).toBe("/app");
    });

    it("falls back for an unknown page of the account zone", () => {
      expect(to("/fr/app/nothing-here")).toBe("/app");
      expect(to(`/fr/app/sources/${OLD}/epg`)).toBe("/app");
      expect(to(`/fr/app/sources/${OLD}/vod/${ITEM}/extra`)).toBe("/app");
    });

    it("falls back when the old source id is not an identifier", () => {
      expect(to("/fr/app/sources/not-a-uuid/vod")).toBe("/app");
      expect(to("/fr/app/sources/../../login/vod")).toBe("/app");
      // Thirty-six characters of the right alphabet, and still not a UUID.
      expect(to(`/fr/app/sources/${"-".repeat(36)}`)).toBe("/app");
    });

    it("falls back for control characters and traversal", () => {
      expect(to("/fr/app/favorites" + String.fromCharCode(10) + "Set-Cookie: x=1")).toBe("/app");
      expect(to("/fr/app/../login")).toBe("/app");
    });

    it("falls back for anything that is not a string", () => {
      expect(to(undefined)).toBe("/app");
      expect(to(null)).toBe("/app");
      expect(to(42)).toBe("/app");
    });

    it("never builds a path from a source id that is not a UUID", () => {
      expect(sourceSwitchTarget(`/fr/app/sources/${OLD}/vod`, "fr", "../../login")).toBe("/app");
      expect(sourceSwitchTarget(`/fr/app/sources/${OLD}/vod`, "fr", "")).toBe("/app");
    });
  });
});
