import { describe, expect, it } from "vitest";
import { hideNoticeOf, homeWithNotice, performHide, readHide } from "./hide-continue";

const SOURCE = "11111111-1111-4111-8111-111111111111";
const ITEM = "33333333-3333-4333-8333-333333333333";

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(values)) data.set(key, value);
  return data;
}

describe("readHide", () => {
  it("reads a well-formed removal", () => {
    expect(readHide(form({ sourceId: SOURCE, itemType: "EPISODE", itemRef: ITEM }))).toEqual({
      sourceId: SOURCE,
      itemType: "EPISODE",
      itemRef: ITEM,
    });
  });

  it("refuses anything malformed", () => {
    expect(readHide(form({ sourceId: "nope", itemType: "VOD", itemRef: ITEM }))).toBeNull();
    expect(readHide(form({ sourceId: SOURCE, itemType: "LIVE", itemRef: ITEM }))).toBeNull();
    expect(readHide(form({ sourceId: SOURCE, itemType: "VOD", itemRef: "" }))).toBeNull();
    expect(
      readHide(form({ sourceId: SOURCE, itemType: "VOD", itemRef: "x".repeat(201) })),
    ).toBeNull();
  });
});

describe("performHide", () => {
  const request = { sourceId: SOURCE, itemType: "VOD" as const, itemRef: ITEM };

  it("is hidden on 204 only", async () => {
    expect(await performHide(request, async () => 204)).toBe("hidden");
  });

  it("is unconfirmed on a refusal, no answer, or a malformed form (CW-26)", async () => {
    expect(await performHide(request, async () => 500)).toBe("unconfirmed");
    expect(await performHide(request, async () => 404)).toBe("unconfirmed");
    expect(
      await performHide(request, async () => {
        throw new Error("down");
      }),
    ).toBe("unconfirmed");
    let sent = false;
    expect(
      await performHide(null, async () => {
        sent = true;
        return 204;
      }),
    ).toBe("unconfirmed");
    expect(sent).toBe(false);
  });
});

describe("notice", () => {
  it("round-trips through the query", () => {
    expect(homeWithNotice("unconfirmed")).toBe("/app?cw=unconfirmed");
    expect(hideNoticeOf("hidden")).toBe("hidden");
    expect(hideNoticeOf(["unconfirmed"])).toBe("unconfirmed");
    expect(hideNoticeOf("anything")).toBeNull();
    expect(hideNoticeOf(undefined)).toBeNull();
  });
});
