import { describe, expect, it } from "vitest";
import { deletionFailure, deletionOutcome } from "./account-deletion";

describe("deletionOutcome", () => {
  it("calls only a 204 a deletion", () => {
    expect(deletionOutcome({ status: 204 })).toBe("deleted");
    expect(deletionOutcome({ status: 200 })).toBe("failed");
  });

  it("ends the session on a 401, without announcing a deletion", () => {
    expect(deletionOutcome({ status: 401, code: "UNAUTHENTICATED" })).toBe("signed-out");
  });

  it("reads a 403 as a wrong password", () => {
    expect(deletionOutcome({ status: 403, code: "INVALID_CREDENTIALS" })).toBe("wrong-password");
  });

  it("reads a 429 as too many attempts", () => {
    expect(deletionOutcome({ status: 429, code: "RATE_LIMITED" })).toBe("rate-limited");
  });

  it("falls back to failed on an outage or an unknown answer", () => {
    expect(deletionOutcome({ status: 0 })).toBe("failed");
    expect(deletionOutcome({ status: 500, code: "INTERNAL_ERROR" })).toBe("failed");
    expect(deletionOutcome({ status: 409, code: "SOMETHING_NEW" })).toBe("failed");
  });
});

describe("deletionFailure", () => {
  it("accepts only the failures the page has a message for", () => {
    expect(deletionFailure("wrong-password")).toBe("wrong-password");
    expect(deletionFailure("deleted")).toBeNull();
    expect(deletionFailure(["failed"])).toBeNull();
    expect(deletionFailure(undefined)).toBeNull();
  });
});
