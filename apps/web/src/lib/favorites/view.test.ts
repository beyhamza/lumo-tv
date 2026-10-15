import { describe, expect, it } from "vitest";
import { favoritesView } from "./view";

// Bench identifiers only: no channel is named anywhere in this repository.
const SOURCE = "source-a";
const OTHER_SOURCE = "source-b";

const groups = [
  { id: "g-first", position: 0 },
  { id: "g-second", position: 1 },
];

let next = 0;
function fav(channel: string, group: string, position: number, source = SOURCE) {
  next += 1;
  return { id: `f${next}`, group_id: group, source_id: source, channel_id: channel, position };
}

// c1 is filed in both groups — the duplicate the All view used to show twice.
const favorites = [
  fav("c2", "g-first", 1),
  fav("c1", "g-first", 0),
  fav("x1", "g-first", 2, OTHER_SOURCE),
  fav("c3", "g-second", 0),
  fav("c1", "g-second", 1),
];

describe("favoritesView — All favourites (S11-02)", () => {
  const view = favoritesView(favorites, groups, SOURCE, undefined);

  it("lists each channel once, groups then positions, first occurrence wins", () => {
    if (view.kind !== "all") throw new Error("expected the All view");
    expect(view.rows.map((row) => row.favorite.channel_id)).toEqual(["c1", "c2", "c3"]);
  });

  it("carries every membership of a channel, for Organise and Remove from all", () => {
    if (view.kind !== "all") throw new Error("expected the All view");
    const c1 = view.rows.find((row) => row.favorite.channel_id === "c1");
    expect(c1?.memberships.map((m) => m.group_id)).toEqual(["g-first", "g-second"]);
  });

  it("FO-10: removing the first occurrence moves the card to the next one", () => {
    const withoutFirst = favorites.filter(
      (f) => !(f.channel_id === "c1" && f.group_id === "g-first"),
    );
    const after = favoritesView(withoutFirst, groups, SOURCE, undefined);
    if (after.kind !== "all") throw new Error("expected the All view");
    expect(after.rows.map((row) => row.favorite.channel_id)).toEqual(["c2", "c3", "c1"]);
  });

  it("never lists another source's favourites", () => {
    if (view.kind !== "all") throw new Error("expected the All view");
    expect(view.rows.some((row) => row.favorite.source_id === OTHER_SOURCE)).toBe(false);
  });
});

describe("favoritesView — one group", () => {
  it("is the group's raw rows for the active source, in position order", () => {
    const view = favoritesView(favorites, groups, SOURCE, "g-first");
    expect(view.kind).toBe("group");
    if (view.kind !== "group") return;
    expect(view.rows.map((f) => f.channel_id)).toEqual(["c1", "c2"]);
  });

  it("falls back to All favourites for a group that no longer exists", () => {
    expect(favoritesView(favorites, groups, SOURCE, "g-gone").kind).toBe("all");
  });
});
