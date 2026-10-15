import { describe, expect, it } from "vitest";
import {
  classifyDelete,
  executeRemoval,
  MAX_MEMBERSHIPS,
  membershipsOf,
  readMembershipIds,
  stillRemaining,
} from "./global-removal";
import { cleanView, confirmView, readConfirm, readNotice, withNotice } from "./notice";

// Bench identifiers only: no channel is named anywhere in this repository.
const SOURCE = "11111111-1111-4111-8111-111111111111";
const OTHER_SOURCE = "22222222-2222-4222-8222-222222222222";
const CHANNEL = "33333333-3333-4333-8333-333333333333";
const OTHER_CHANNEL = "44444444-4444-4444-8444-444444444444";

const F1 = "aaaaaaaa-0000-4000-8000-000000000001";
const F2 = "aaaaaaaa-0000-4000-8000-000000000002";
const F3 = "aaaaaaaa-0000-4000-8000-000000000003";
const F4 = "aaaaaaaa-0000-4000-8000-000000000004";

const groups = [
  { id: "g-second", position: 1 },
  { id: "g-first", position: 0 },
  { id: "g-third", position: 2 },
];

const favorites = [
  { id: F1, group_id: "g-third", source_id: SOURCE, channel_id: CHANNEL, position: 0 },
  { id: F2, group_id: "g-first", source_id: SOURCE, channel_id: CHANNEL, position: 3 },
  { id: F3, group_id: "g-second", source_id: SOURCE, channel_id: CHANNEL, position: 1 },
  { id: F4, group_id: "g-first", source_id: SOURCE, channel_id: OTHER_CHANNEL, position: 0 },
  {
    id: "aaaaaaaa-0000-4000-8000-000000000005",
    group_id: "g-first",
    source_id: OTHER_SOURCE,
    channel_id: CHANNEL,
    position: 1,
  },
];

describe("membershipsOf", () => {
  it("lists every membership of the channel in group order, this source only", () => {
    expect(membershipsOf(favorites, groups, SOURCE, CHANNEL).map((f) => f.id)).toEqual([
      F2,
      F3,
      F1,
    ]);
  });
});

describe("readMembershipIds", () => {
  it("keeps uuids, drops the rest, deduplicates", () => {
    expect(readMembershipIds([F1, "nope", F1, F2.toUpperCase(), null])).toEqual([F1, F2]);
  });

  it("sends nothing for an oversized list", () => {
    const many = Array.from(
      { length: MAX_MEMBERSHIPS + 1 },
      (_, i) => `aaaaaaaa-0000-4000-8000-${String(i).padStart(12, "0")}`,
    );
    expect(readMembershipIds(many)).toEqual([]);
  });
});

describe("classifyDelete", () => {
  it("204 is a removal", () => {
    expect(classifyDelete({ status: 204 })).toBe("removed");
  });

  it("404 FAVORITE_NOT_FOUND is already gone, hence removed", () => {
    expect(classifyDelete({ status: 404, error: { code: "FAVORITE_NOT_FOUND" } })).toBe(
      "removed",
    );
  });

  it("any other answer is not confirmed", () => {
    expect(classifyDelete({ status: 404, error: { code: "SOMETHING_ELSE" } })).toBe(
      "not-confirmed",
    );
    expect(classifyDelete({ status: 500 })).toBe("not-confirmed");
    expect(classifyDelete({ status: 401 })).toBe("not-confirmed");
  });
});

describe("executeRemoval (G2, FO-05 to FO-08)", () => {
  it("FO-05: two of three succeed — both kept, the third reported as left", async () => {
    const sent: string[] = [];
    const outcome = await executeRemoval([F2, F3, F1], async (id) => {
      sent.push(id);
      return id === F3 ? { status: 503 } : { status: 204 };
    });
    expect(outcome).toEqual({ removed: [F2, F1], left: [F3] });
    // Sequential, every one attempted: a failure does not cancel the others.
    expect(sent).toEqual([F2, F3, F1]);
  });

  it("an already-removed membership counts as removed", async () => {
    const outcome = await executeRemoval([F1, F2], async (id) =>
      id === F1 ? { status: 404, error: { code: "FAVORITE_NOT_FOUND" } } : { status: 204 },
    );
    expect(outcome).toEqual({ removed: [F1, F2], left: [] });
  });

  it("FO-08: a lost response is left, not invented as removed", async () => {
    const outcome = await executeRemoval([F1], async () => {
      throw new Error("connection reset");
    });
    expect(outcome).toEqual({ removed: [], left: [F1] });
  });

  it("FO-06: the retry carries only what was left", async () => {
    const sent: string[] = [];
    const first = await executeRemoval([F2, F3, F1], async (id) =>
      id === F3 ? { status: 503 } : { status: 204 },
    );
    await executeRemoval(first.left, async (id) => {
      sent.push(id);
      return { status: 204 };
    });
    expect(sent).toEqual([F3]);
  });
});

describe("stillRemaining", () => {
  it("FO-08: a membership the re-read no longer has was removed after all", () => {
    const reread = favorites.filter((f) => f.id !== F3);
    expect(stillRemaining([F3], reread)).toEqual([]);
  });

  it("keeps the memberships that really remain", () => {
    expect(stillRemaining([F3], favorites).map((f) => f.id)).toEqual([F3]);
  });

  it("FO-12: never widens to a membership added since the confirmation", () => {
    const added = {
      id: "aaaaaaaa-0000-4000-8000-000000000009",
      group_id: "g-second",
      source_id: SOURCE,
      channel_id: CHANNEL,
      position: 5,
    };
    const ids = stillRemaining([F3], [...favorites, added]).map((f) => f.id);
    expect(ids).toEqual([F3]);
  });
});

describe("notice in the redirect", () => {
  it("round-trips a partial removal", () => {
    const href = withNotice("/app/favorites", {
      kind: "removal-partial",
      channelId: CHANNEL,
      left: [F3],
    });
    const query = Object.fromEntries(new URLSearchParams(href.split("?")[1]));
    expect(readNotice(query)).toEqual({ kind: "removal-partial", channelId: CHANNEL, left: [F3] });
  });

  it("drops the previous notice and the confirmation on the way back", () => {
    const href = withNotice(
      `/app/favorites?group=g&fav=move-partial&confirm=${CHANNEL}`,
      null,
    );
    expect(href).toBe("/app/favorites?group=g");
  });

  it("reads a malformed notice as no notice", () => {
    expect(readNotice({ fav: "removal-partial", channel: CHANNEL, left: "x,y" })).toBeNull();
    expect(readNotice({ fav: "everything-fine" })).toBeNull();
  });

  it("reads only a uuid as a confirmation target", () => {
    expect(readConfirm({ confirm: CHANNEL })).toBe(CHANNEL);
    expect(readConfirm({ confirm: "../x" })).toBeUndefined();
  });

  it("the confirmation link survives as a link (it is not stripped like a notice)", () => {
    const href = confirmView(cleanView("/app/favorites", { fav: "unconfirmed" }), CHANNEL);
    expect(href).toBe(`/app/favorites?confirm=${CHANNEL}`);
    const query = Object.fromEntries(new URLSearchParams(href.split("?")[1]));
    expect(readConfirm(query)).toBe(CHANNEL);
  });

  it("the clean view keeps the group and drops what actions own", () => {
    expect(
      cleanView("/app/favorites", { group: "g", fav: "unconfirmed", confirm: CHANNEL }),
    ).toBe("/app/favorites?group=g");
  });
});
