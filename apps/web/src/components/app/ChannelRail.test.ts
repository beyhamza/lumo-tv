import { describe, expect, it } from "vitest";
import { railParts } from "./ChannelRail";

/**
 * Which rail exists when it has no card (US-017, S9-04-07).
 *
 * The home page draws three rails the same way; what must not be the same is
 * what happens when one of them is empty. The Live rail carries the two
 * explicit doors to the catalogue and the guide, and they are needed **before**
 * a first read — a cold account has no recent channel. The Favourites rail has
 * a single entry, and "Voir tous les favoris" over nothing is a cul-de-sac, so
 * it stays hidden. This pins the two rules apart.
 */
describe("railParts", () => {
  it("hides a rail with no card and no entry", () => {
    expect(railParts(0, 0, false)).toBeNull();
    // Opting in cannot conjure entries that do not exist.
    expect(railParts(0, 0, true)).toBeNull();
  });

  it("hides the Favourites rail at cold, even though it carries an entry", () => {
    // "Voir tous les favoris" over an empty list is a cul-de-sac (US-017).
    expect(railParts(0, 1, false)).toBeNull();
  });

  it("keeps the Live rail's two doors on a cold account, with no card", () => {
    // S9-04-07: "Toutes les chaînes" and "Guide TV" exist before any history.
    expect(railParts(0, 2, true)).toEqual({ cards: false, entries: true });
  });

  it("draws the cards, and the entries beside them, once there is a channel", () => {
    expect(railParts(3, 2, true)).toEqual({ cards: true, entries: true });
    expect(railParts(1, 0, false)).toEqual({ cards: true, entries: false });
  });
});
