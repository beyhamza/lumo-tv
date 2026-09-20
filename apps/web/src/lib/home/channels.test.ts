import { describe, expect, it } from "vitest";
import { aggregateFavorites, recentChannelsOf } from "./channels";

// Bench identifiers only. The rules are about order and identity, not about what
// a channel is called, and no real channel is named anywhere in this repository.
const SOURCE = "11111111-1111-4111-8111-111111111111";
const OTHER_SOURCE = "22222222-2222-4222-8222-222222222222";

const GROUP_FIRST = { id: "group-first", position: 0 };
const GROUP_SECOND = { id: "group-second", position: 1 };

let nextId = 0;
function favorite(
  channelId: string,
  group: { id: string },
  position: number,
  sourceId = SOURCE,
) {
  nextId += 1;
  return {
    id: `favorite-${nextId}`,
    group_id: group.id,
    source_id: sourceId,
    channel_id: channelId,
    position,
  };
}

const channelIds = (favorites: { channel_id: string }[]) =>
  favorites.map((entry) => entry.channel_id);

/**
 * The aggregated favourites of the home page (US-020).
 *
 * Each case is a way for the rail to be wrong while looking fine: a card drawn
 * twice, a card in the place its *second* group gave it, another source's
 * channel under this source's name.
 */
describe("aggregateFavorites", () => {
  it("walks the groups in their order, then the channels in theirs", () => {
    const favorites = [
      favorite("bench-c", GROUP_SECOND, 0),
      favorite("bench-b", GROUP_FIRST, 1),
      favorite("bench-a", GROUP_FIRST, 0),
    ];

    expect(
      channelIds(aggregateFavorites(favorites, [GROUP_FIRST, GROUP_SECOND], SOURCE)),
    ).toEqual(["bench-a", "bench-b", "bench-c"]);
  });

  it("orders groups by position, not by where they sit in the answer", () => {
    const favorites = [favorite("bench-a", GROUP_FIRST, 0), favorite("bench-c", GROUP_SECOND, 0)];

    // The group list arrives reversed; `position` is what the user arranged.
    expect(
      channelIds(aggregateFavorites(favorites, [GROUP_SECOND, GROUP_FIRST], SOURCE)),
    ).toEqual(["bench-a", "bench-c"]);
  });

  it("shows a channel filed in two groups once, where its first group puts it", () => {
    const favorites = [
      favorite("bench-a", GROUP_FIRST, 0),
      favorite("bench-shared", GROUP_FIRST, 1),
      favorite("bench-shared", GROUP_SECOND, 0),
      favorite("bench-c", GROUP_SECOND, 1),
    ];

    expect(
      channelIds(aggregateFavorites(favorites, [GROUP_FIRST, GROUP_SECOND], SOURCE)),
    ).toEqual(["bench-a", "bench-shared", "bench-c"]);
  });

  it("follows the groups when they are reordered — no ranking of its own", () => {
    const favorites = [
      favorite("bench-a", GROUP_FIRST, 0),
      favorite("bench-shared", GROUP_FIRST, 1),
      favorite("bench-shared", GROUP_SECOND, 0),
      favorite("bench-c", GROUP_SECOND, 1),
    ];
    // The second group is moved to the front in the library.
    const moved = [
      { ...GROUP_SECOND, position: 0 },
      { ...GROUP_FIRST, position: 1 },
    ];

    expect(channelIds(aggregateFavorites(favorites, moved, SOURCE))).toEqual([
      "bench-shared",
      "bench-c",
      "bench-a",
    ]);
  });

  it("returns the first membership, so the next one takes over if it is removed", () => {
    const first = favorite("bench-shared", GROUP_FIRST, 0);
    const second = favorite("bench-shared", GROUP_SECOND, 0);
    const groups = [GROUP_FIRST, GROUP_SECOND];

    expect(aggregateFavorites([first, second], groups, SOURCE)).toEqual([first]);
    // A global removal that only succeeded in the first group (19 September
    // 2026): the channel stays, at the place its remaining membership gives it.
    expect(aggregateFavorites([second], groups, SOURCE)).toEqual([second]);
  });

  it("keeps only the active source", () => {
    const favorites = [
      favorite("bench-elsewhere", GROUP_FIRST, 0, OTHER_SOURCE),
      favorite("bench-a", GROUP_FIRST, 1),
    ];

    expect(channelIds(aggregateFavorites(favorites, [GROUP_FIRST], SOURCE))).toEqual([
      "bench-a",
    ]);
  });

  it("puts a favourite of an unknown group last instead of dropping it", () => {
    // A group created on another device between the two requests.
    const favorites = [
      favorite("bench-new", { id: "group-unknown" }, 0),
      favorite("bench-a", GROUP_FIRST, 0),
    ];

    expect(channelIds(aggregateFavorites(favorites, [GROUP_FIRST], SOURCE))).toEqual([
      "bench-a",
      "bench-new",
    ]);
  });

  it("keeps the server's order when the group list could not be read", () => {
    // `null` is "did not answer". The contract orders favourites by group then
    // position already, so the rail is the same one — deduplicated all the same.
    const favorites = [
      favorite("bench-b", GROUP_SECOND, 0),
      favorite("bench-a", GROUP_FIRST, 0),
      favorite("bench-b", GROUP_FIRST, 1),
    ];

    expect(channelIds(aggregateFavorites(favorites, null, SOURCE))).toEqual([
      "bench-b",
      "bench-a",
    ]);
  });

  it("answers an empty list for an account with no favourite", () => {
    expect(aggregateFavorites([], [GROUP_FIRST], SOURCE)).toEqual([]);
  });
});

describe("recentChannelsOf", () => {
  const recent = (channelId: string, sourceId = SOURCE) => ({
    channel_id: channelId,
    source_id: sourceId,
  });

  it("keeps the active source, in the server's order", () => {
    const recents = [
      recent("bench-3"),
      recent("bench-elsewhere", OTHER_SOURCE),
      recent("bench-1"),
      recent("bench-2"),
    ];

    expect(channelIds(recentChannelsOf(recents, SOURCE, 12))).toEqual([
      "bench-3",
      "bench-1",
      "bench-2",
    ]);
  });

  it("caps after filtering, so another source's channels cost no slot", () => {
    const recents = [
      recent("bench-elsewhere-1", OTHER_SOURCE),
      recent("bench-elsewhere-2", OTHER_SOURCE),
      recent("bench-1"),
      recent("bench-2"),
      recent("bench-3"),
    ];

    expect(channelIds(recentChannelsOf(recents, SOURCE, 2))).toEqual(["bench-1", "bench-2"]);
  });

  it("never shows the same channel twice", () => {
    expect(
      channelIds(recentChannelsOf([recent("bench-1"), recent("bench-1")], SOURCE, 12)),
    ).toEqual(["bench-1"]);
  });
});
