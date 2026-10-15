import { describe, expect, it } from "vitest";
import { channelRailStatus, homeState, lookupOf, railStatus } from "./rail-status";

/**
 * Per-rail status on the home page (S11-05).
 *
 * The defect this pins: a lookup failing in the second wave left its rail empty
 * with nothing marked failed, and the page told somebody with a history that
 * there was nothing yet.
 */
describe("lookupOf", () => {
  it("tells a request never asked from one that did not answer", () => {
    expect(lookupOf(false, undefined)).toBe("skipped");
    expect(lookupOf(true, undefined)).toBe("failed");
    expect(lookupOf(true, { items: [] })).toBe("ok");
  });
});

describe("railStatus", () => {
  it("is failed as soon as one request it depends on failed", () => {
    expect(railStatus("ok", "skipped", "ok")).toBe("ok");
    expect(railStatus("ok", "failed", "skipped")).toBe("failed");
    expect(railStatus()).toBe("ok");
  });
});

describe("channelRailStatus", () => {
  it("fails when the list itself did not answer", () => {
    expect(channelRailStatus("failed", [], new Set())).toBe("failed");
  });

  it("fails when the lookup of one of its own channels failed", () => {
    // The second-wave case: the favourites answered, their names did not.
    expect(channelRailStatus("ok", ["a", "b"], new Set(["b"]))).toBe("failed");
  });

  it("ignores failed ids that belong to the other rail", () => {
    // The lookup is shared by both channel rails.
    expect(channelRailStatus("ok", ["a"], new Set(["z"]))).toBe("ok");
  });

  it("is ok for an empty list that answered", () => {
    expect(channelRailStatus("ok", [], new Set())).toBe("ok");
  });
});

describe("homeState", () => {
  const ok = (cards: number) => ({ cards, status: "ok" as const });
  const failed = (cards: number) => ({ cards, status: "failed" as const });

  it("invites to explore only when every rail answered empty", () => {
    expect(homeState([ok(0), ok(0), ok(0)])).toBe("explore");
  });

  it("never invites to explore when a rail failed, even if the others are empty", () => {
    // The second-wave defect: this used to be "explore".
    expect(homeState([ok(0), failed(0), ok(0)])).toBe("rails");
  });

  it("is unavailable only when every rail failed and none has a card", () => {
    expect(homeState([failed(0), failed(0), failed(0)])).toBe("unavailable");
  });

  it("draws the rails whenever one has cards, failed or not", () => {
    expect(homeState([failed(2), failed(0), failed(0)])).toBe("rails");
    expect(homeState([ok(0), ok(3), failed(0)])).toBe("rails");
  });
});
