import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import fr from "@/messages/fr.json";
import { playbackRefusal } from "./refusal";

/**
 * What a player says when the API will not hand over a stream (C4 — P2).
 */
describe("playbackRefusal", () => {
  it("words SOURCE_NOT_READY as a refresh in progress, with the way to follow it", () => {
    // Not the `Errors` sentence about an unfinished import: since C4 a catalogue
    // that can be browsed has been imported, and what is running is a refresh.
    expect(playbackRefusal("SOURCE_NOT_READY")).toEqual({
      message: { namespace: "App", key: "playerSourceRefreshing" },
      sourceLink: "follow",
    });
  });

  it("sends refused credentials and an expired subscription to fix the source", () => {
    expect(playbackRefusal("SOURCE_AUTH_FAILED")).toEqual({
      message: { namespace: "Errors", key: "SOURCE_AUTH_FAILED" },
      sourceLink: "fix",
    });
    expect(playbackRefusal("SOURCE_EXPIRED")).toEqual({
      message: { namespace: "Errors", key: "SOURCE_EXPIRED" },
      sourceLink: "fix",
    });
  });

  it("offers no source link where the source page would not help", () => {
    expect(playbackRefusal("SOURCE_MAX_CONNECTIONS").sourceLink).toBeNull();
    expect(playbackRefusal("CHANNEL_NOT_FOUND").sourceLink).toBeNull();
  });

  it("degrades on a code nobody has seen", () => {
    expect(playbackRefusal("SOMETHING_NEW")).toEqual({
      message: { namespace: "App", key: "playerUnplayable" },
      sourceLink: null,
    });
    expect(playbackRefusal("")).toEqual(playbackRefusal("SOMETHING_NEW"));
  });

  it("only names messages that exist in both locales", () => {
    const codes = [
      "SOURCE_NOT_READY",
      "SOURCE_AUTH_FAILED",
      "SOURCE_EXPIRED",
      "SOURCE_MAX_CONNECTIONS",
      "SOURCE_NOT_FOUND",
      "CHANNEL_NOT_FOUND",
      "VOD_ITEM_NOT_FOUND",
      "EPISODE_NOT_FOUND",
      "UNAUTHENTICATED",
      "NETWORK",
      "SOMETHING_NEW",
    ];
    for (const code of codes) {
      const { message } = playbackRefusal(code);
      for (const catalogue of [fr, en]) {
        const namespace = catalogue[message.namespace] as Record<string, string>;
        expect(namespace[message.key], `${message.namespace}.${message.key}`).toBeTypeOf("string");
      }
    }
  });
});
