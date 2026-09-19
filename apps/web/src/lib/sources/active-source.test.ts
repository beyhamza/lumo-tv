import { describe, expect, it } from "vitest";
import {
  activeSourceCookieName,
  isUuid,
  resolveActiveSource,
  storedSourceId,
} from "./active-source";

// Bench identifiers. Nothing here names a real provider, and nothing needs to:
// the rules are about how many sources there are, not about what they hold.
const SOURCE_A = { id: "11111111-1111-4111-8111-111111111111", label: "Bench A" };
const SOURCE_B = { id: "22222222-2222-4222-8222-222222222222", label: "Bench B" };
const SOURCE_C = { id: "33333333-3333-4333-8333-333333333333", label: "Bench C" };
const GONE = "99999999-9999-4999-8999-999999999999";

const ACCOUNT_ONE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const ACCOUNT_TWO = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

/**
 * The active-source decision (US-018).
 *
 * Every branch, because each wrong answer here is silent: a source picked among
 * several looks like a working screen, and the user finds out when their
 * favourites are "gone".
 */
describe("resolveActiveSource", () => {
  it("answers none for an account with no source", () => {
    expect(resolveActiveSource([], null)).toEqual({ state: "none" });
  });

  it("answers none with no source even when a choice is still stored", () => {
    // The last source was deleted elsewhere: the way out is "add a source", not
    // a chooser with nothing in it.
    expect(resolveActiveSource([], GONE)).toEqual({ state: "none" });
  });

  it("selects the stored source when it is still in the list", () => {
    expect(resolveActiveSource([SOURCE_A, SOURCE_B], SOURCE_B.id)).toEqual({
      state: "selected",
      source: SOURCE_B,
    });
  });

  it("compares identifiers without regard to case", () => {
    expect(
      resolveActiveSource([SOURCE_A, SOURCE_B], SOURCE_B.id.toUpperCase()),
    ).toEqual({ state: "selected", source: SOURCE_B });
  });

  it("selects the only source when nothing is stored", () => {
    expect(resolveActiveSource([SOURCE_A], null)).toEqual({
      state: "selected",
      source: SOURCE_A,
    });
  });

  it("asks when several sources exist and nothing is stored", () => {
    // Never the first of the list: that would be this code choosing a catalogue
    // on the user's behalf.
    expect(resolveActiveSource([SOURCE_A, SOURCE_B], null)).toEqual({
      state: "needs-choice",
    });
  });

  it("selects the one source left when the stored one is gone", () => {
    // Deleted here or on another device — the list is the only evidence either
    // way, and it reads the same.
    expect(resolveActiveSource([SOURCE_A], GONE)).toEqual({
      state: "selected",
      source: SOURCE_A,
    });
  });

  it("asks when the stored source is gone and several are left", () => {
    expect(resolveActiveSource([SOURCE_A, SOURCE_B, SOURCE_C], GONE)).toEqual({
      state: "needs-choice",
    });
  });
});

describe("the stored choice", () => {
  const jar = (entries: Record<string, string>) => (name: string) => entries[name];

  it("names one cookie per account", () => {
    expect(activeSourceCookieName(ACCOUNT_ONE)).toBe(`lumo_active_source_${ACCOUNT_ONE}`);
    expect(activeSourceCookieName(ACCOUNT_ONE)).not.toBe(activeSourceCookieName(ACCOUNT_TWO));
  });

  it("refuses to build a cookie name from anything but a UUID", () => {
    expect(activeSourceCookieName("")).toBeNull();
    expect(activeSourceCookieName("me; Path=/")).toBeNull();
    expect(activeSourceCookieName(`${ACCOUNT_ONE}=x`)).toBeNull();
  });

  it("reads back what was stored for this account", () => {
    const read = jar({ [`lumo_active_source_${ACCOUNT_ONE}`]: SOURCE_A.id });
    expect(storedSourceId(read, ACCOUNT_ONE)).toBe(SOURCE_A.id);
  });

  it("does not let two accounts in one browser share a choice", () => {
    // The shared laptop: the first account chose A, the second signs in. It must
    // read nothing — A is not even a source it owns.
    const read = jar({ [`lumo_active_source_${ACCOUNT_ONE}`]: SOURCE_A.id });
    expect(storedSourceId(read, ACCOUNT_TWO)).toBeNull();

    const both = jar({
      [`lumo_active_source_${ACCOUNT_ONE}`]: SOURCE_A.id,
      [`lumo_active_source_${ACCOUNT_TWO}`]: SOURCE_B.id,
    });
    expect(storedSourceId(both, ACCOUNT_ONE)).toBe(SOURCE_A.id);
    expect(storedSourceId(both, ACCOUNT_TWO)).toBe(SOURCE_B.id);
  });

  it("reads a value that is not a UUID as nothing stored", () => {
    for (const value of ["", "undefined", "../../login", `${SOURCE_A.id}/vod`, "1 OR 1=1"]) {
      const read = jar({ [`lumo_active_source_${ACCOUNT_ONE}`]: value });
      expect(storedSourceId(read, ACCOUNT_ONE)).toBeNull();
    }
  });

  it("reads nothing for an account id that is not a UUID", () => {
    expect(storedSourceId(jar({ lumo_active_source_x: SOURCE_A.id }), "x")).toBeNull();
  });
});

describe("isUuid", () => {
  it("accepts the contract's identifiers and nothing else", () => {
    expect(isUuid(SOURCE_A.id)).toBe(true);
    expect(isUuid(SOURCE_A.id.toUpperCase())).toBe(true);
    expect(isUuid(SOURCE_A.id.replaceAll("-", ""))).toBe(false);
    expect(isUuid(` ${SOURCE_A.id}`)).toBe(false);
    expect(isUuid(`${SOURCE_A.id}\n`)).toBe(false);
    expect(isUuid(null)).toBe(false);
    expect(isUuid(undefined)).toBe(false);
    expect(isUuid(42)).toBe(false);
  });
});
