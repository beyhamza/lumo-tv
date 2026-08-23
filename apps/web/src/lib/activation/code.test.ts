import { describe, expect, it } from "vitest";
import { normaliseActivationCode } from "./code";

/**
 * Whatever someone types while looking at a television, turned into what the
 * server matches (US-05).
 */
describe("normaliseActivationCode", () => {
  it("keeps a plain code untouched", () => {
    expect(normaliseActivationCode("ABCD2345")).toBe("ABCD2345");
  });

  it("accepts the separator people read off the screen", () => {
    expect(normaliseActivationCode("K7RM-4XPQ")).toBe("K7RM4XPQ");
  });

  it("accepts lower case and spaces", () => {
    expect(normaliseActivationCode("k7rm 4xpq")).toBe("K7RM4XPQ");
    expect(normaliseActivationCode("  abcd2345  ")).toBe("ABCD2345");
  });

  it("strips a non-breaking space, which pasting from a page introduces", () => {
    expect(normaliseActivationCode("ABCD" + String.fromCharCode(160) + "2345")).toBe(
      "ABCD2345",
    );
  });

  it("caps the length so a pathological input never reaches the API", () => {
    expect(normaliseActivationCode("A".repeat(500))).toHaveLength(16);
  });

  it("returns an empty string when there is nothing usable", () => {
    expect(normaliseActivationCode("")).toBe("");
    expect(normaliseActivationCode("----")).toBe("");
  });
});
