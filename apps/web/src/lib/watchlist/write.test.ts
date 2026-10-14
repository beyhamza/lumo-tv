import { describe, expect, it, vi } from "vitest";
import {
  INTENTION_KEY_FIELD,
  classifyAnswer,
  currentView,
  isUnconfirmed,
  newIntentionKey,
  performWrite,
  readAdd,
  readRemove,
  returnWithNotice,
  type WatchlistWrite,
  type WriteAnswer,
} from "./write";

const KEY = "7d3f2a10-4c5b-4e6f-8a9b-0c1d2e3f4a5b";
const CONTENT = "2a1b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
const ITEM = "1f0e6a3c-2b7d-4e8f-9a01-23456789abcd";

function addForm(key: string | null = KEY): FormData {
  const form = new FormData();
  if (key !== null) form.set(INTENTION_KEY_FIELD, key);
  form.set("contentType", "VOD");
  form.set("contentId", CONTENT);
  return form;
}

function removeForm(key: string | null = KEY): FormData {
  const form = new FormData();
  if (key !== null) form.set(INTENTION_KEY_FIELD, key);
  form.set("itemId", ITEM);
  return form;
}

describe("newIntentionKey", () => {
  it("mints a UUID the forms accept, a different one per render", () => {
    const first = newIntentionKey();
    const second = newIntentionKey();
    expect(first).not.toBe(second);
    expect(readAdd(addForm(first))?.key).toBe(first);
  });
});

describe("reading the form", () => {
  it("keeps the hidden key exactly as rendered", () => {
    expect(readAdd(addForm())).toEqual({
      kind: "add",
      key: KEY,
      contentType: "VOD",
      contentId: CONTENT,
    });
    expect(readRemove(removeForm())).toEqual({ kind: "remove", key: KEY, itemId: ITEM });
  });

  it("refuses a missing or malformed key instead of minting one", () => {
    expect(readAdd(addForm(null))).toBeNull();
    expect(readAdd(addForm(""))).toBeNull();
    expect(readAdd(addForm("not-a-uuid"))).toBeNull();
    expect(readRemove(removeForm(null))).toBeNull();
    expect(readRemove(removeForm(`${KEY}x`))).toBeNull();
  });

  it("refuses an unknown content type or a malformed id", () => {
    const episode = addForm();
    episode.set("contentType", "EPISODE");
    expect(readAdd(episode)).toBeNull();

    const badId = removeForm();
    badId.set("itemId", "../sources");
    expect(readRemove(badId)).toBeNull();
  });
});

describe("performWrite", () => {
  it("forwards the form's key to the API unchanged", async () => {
    const send = vi.fn(async (): Promise<WriteAnswer> => ({ status: 201 }));
    await performWrite(readAdd(addForm()), send);
    expect(send).toHaveBeenCalledWith(expect.objectContaining({ key: KEY }));
  });

  it("sends the same key again when the same form is submitted twice", async () => {
    const keys: string[] = [];
    const send = async (write: WatchlistWrite): Promise<WriteAnswer> => {
      keys.push(write.key);
      // The server replays: same status, nothing changed.
      return { status: 204 };
    };
    const form = removeForm();

    expect(await performWrite(readRemove(form), send)).toBe("applied");
    expect(await performWrite(readRemove(form), send)).toBe("applied");
    expect(keys).toEqual([KEY, KEY]);
  });

  it("sends nothing for a malformed form, and does not claim success", async () => {
    const send = vi.fn(async (): Promise<WriteAnswer> => ({ status: 201 }));
    expect(await performWrite(readAdd(addForm("nope")), send)).toBe("unknown");
    expect(send).not.toHaveBeenCalled();
  });

  it("treats no response at all as unknown", async () => {
    const send = async (): Promise<WriteAnswer> => {
      throw new TypeError("fetch failed");
    };
    expect(await performWrite(readAdd(addForm()), send)).toBe("unknown");
  });
});

describe("classifyAnswer", () => {
  it("knows an accepted add, and an item that was already there", () => {
    expect(classifyAnswer("add", { status: 201 })).toBe("applied");
    expect(classifyAnswer("add", { status: 200 })).toBe("already");
  });

  it("knows an accepted removal, and an item already gone", () => {
    expect(classifyAnswer("remove", { status: 204 })).toBe("applied");
    expect(
      classifyAnswer("remove", { status: 404, error: { code: "WATCHLIST_ITEM_NOT_FOUND" } }),
    ).toBe("already");
  });

  it("calls everything else unknown", () => {
    expect(classifyAnswer("add", { status: 404, error: { code: "VOD_ITEM_NOT_FOUND" } })).toBe(
      "unknown",
    );
    expect(classifyAnswer("add", { status: 409, error: { code: "IDEMPOTENCY_KEY_REUSED" } })).toBe(
      "unknown",
    );
    expect(classifyAnswer("add", { status: 500 })).toBe("unknown");
    expect(classifyAnswer("add", { status: 204 })).toBe("unknown");
    expect(classifyAnswer("remove", { status: 404, error: { code: "NOT_FOUND" } })).toBe(
      "unknown",
    );
    expect(classifyAnswer("remove", { status: 404 })).toBe("unknown");
    expect(classifyAnswer("remove", { status: 401, error: { code: "UNAUTHENTICATED" } })).toBe(
      "unknown",
    );
    expect(classifyAnswer("remove", { status: 503 })).toBe("unknown");
  });
});

describe("returnWithNotice / currentView", () => {
  it("adds the notice only for an unknown outcome", () => {
    expect(returnWithNotice("/app/watchlist?type=films", "unknown")).toBe(
      "/app/watchlist?type=films&wl=unconfirmed",
    );
    expect(returnWithNotice("/app/watchlist?type=films", "applied")).toBe(
      "/app/watchlist?type=films",
    );
  });

  it("drops a previous notice", () => {
    expect(returnWithNotice("/app/watchlist?wl=unconfirmed&page=2", "already")).toBe(
      "/app/watchlist?page=2",
    );
    expect(returnWithNotice("/app/watchlist?wl=unconfirmed", "unknown")).toBe(
      "/app/watchlist?wl=unconfirmed",
    );
  });

  it("keeps the view's own query and drops the notice from it", () => {
    expect(
      currentView("/app/sources/x/series/y", { season: "2", wl: "unconfirmed", q: ["a", "b"] }),
    ).toBe("/app/sources/x/series/y?season=2&q=a");
    expect(currentView("/app/watchlist", {})).toBe("/app/watchlist");
  });

  it("reads the notice", () => {
    expect(isUnconfirmed("unconfirmed")).toBe(true);
    expect(isUnconfirmed(["unconfirmed"])).toBe(true);
    expect(isUnconfirmed("1")).toBe(false);
    expect(isUnconfirmed(undefined)).toBe(false);
  });
});
