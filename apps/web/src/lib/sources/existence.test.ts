import { describe, expect, it } from "vitest";
import { existenceFromApi, existenceFromRoute } from "./existence";

/**
 * "This source has been removed from your account" stops somebody's film. It has
 * to be earned by a `404 SOURCE_NOT_FOUND`, and by nothing else (US-024, C4 — P6).
 */
describe("existenceFromApi", () => {
  it("knows the source exists when the API returns it", () => {
    expect(existenceFromApi({ kind: "response", status: 200 })).toBe("exists");
  });

  it("calls it gone on 404 SOURCE_NOT_FOUND", () => {
    expect(
      existenceFromApi({ kind: "response", status: 404, code: "SOURCE_NOT_FOUND" }),
    ).toBe("gone");
  });

  it("does not take a network failure or a timeout for a deletion", () => {
    expect(existenceFromApi({ kind: "no-response" })).toBe("unknown");
  });

  it("does not take a server error for a deletion", () => {
    for (const status of [500, 502, 503, 504]) {
      expect(existenceFromApi({ kind: "response", status, code: "INTERNAL_ERROR" })).toBe(
        "unknown",
      );
      expect(existenceFromApi({ kind: "response", status })).toBe("unknown");
    }
  });

  it("does not take another refusal for a deletion", () => {
    // An expired session, a missing right, a rate limit: the source is where it
    // was, and the film carries on.
    expect(existenceFromApi({ kind: "response", status: 401, code: "UNAUTHENTICATED" })).toBe(
      "unknown",
    );
    expect(existenceFromApi({ kind: "response", status: 403, code: "FORBIDDEN" })).toBe(
      "unknown",
    );
    expect(existenceFromApi({ kind: "response", status: 429, code: "RATE_LIMITED" })).toBe(
      "unknown",
    );
    expect(existenceFromApi({ kind: "response", status: 409, code: "SOURCE_NOT_READY" })).toBe(
      "unknown",
    );
  });

  it("does not take an unrouted 404 for a deletion", () => {
    // The generic code is what an endpoint that is not deployed answers.
    expect(existenceFromApi({ kind: "response", status: 404, code: "NOT_FOUND" })).toBe(
      "unknown",
    );
    expect(existenceFromApi({ kind: "response", status: 404 })).toBe("unknown");
  });

  it("needs the status as well as the code", () => {
    expect(
      existenceFromApi({ kind: "response", status: 500, code: "SOURCE_NOT_FOUND" }),
    ).toBe("unknown");
  });
});

describe("existenceFromRoute", () => {
  it("reads the two answers the handler gives", () => {
    expect(existenceFromRoute(true, { exists: true })).toBe("exists");
    expect(existenceFromRoute(true, { exists: false })).toBe("gone");
  });

  it("ignores the body of a failed response", () => {
    // Whatever it says: a 401 or a 502 is not a verdict.
    expect(existenceFromRoute(false, { exists: false })).toBe("unknown");
    expect(existenceFromRoute(false, { code: "UNAUTHENTICATED" })).toBe("unknown");
  });

  it("answers unknown for anything that is not exactly a boolean", () => {
    for (const body of [null, undefined, "", "false", 0, [], {}, { exists: null }, { exists: "false" }, { exists: 0 }]) {
      expect(existenceFromRoute(true, body)).toBe("unknown");
    }
  });
});
