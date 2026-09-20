import { describe, expect, it } from "vitest";
import { cataloguePageState } from "./page-state";

const OK = { ok: true };

/**
 * A partly available catalogue keeps what loaded and names what did not
 * (US-024). A failed request is never an empty list.
 */
describe("cataloguePageState", () => {
  it("renders everything when both requests answered", () => {
    expect(cataloguePageState(OK, OK)).toEqual({
      kind: "ok",
      listingFailed: false,
      categoriesFailed: false,
    });
  });

  it("keeps the listing when only the categories failed", () => {
    expect(cataloguePageState(OK, { ok: false, code: "INTERNAL_ERROR" })).toEqual({
      kind: "ok",
      listingFailed: false,
      categoriesFailed: true,
    });
    // No response at all is the same partial page.
    expect(cataloguePageState(OK, { ok: false })).toEqual({
      kind: "ok",
      listingFailed: false,
      categoriesFailed: true,
    });
  });

  it("keeps the categories when only the listing failed", () => {
    expect(cataloguePageState({ ok: false, code: "INTERNAL_ERROR" }, OK)).toEqual({
      kind: "ok",
      listingFailed: true,
      categoriesFailed: false,
    });
  });

  it("is not-ready when no catalogue was ever ingested", () => {
    const notReady = { ok: false, code: "SOURCE_NOT_READY" };
    expect(cataloguePageState(notReady, notReady)).toEqual({ kind: "not-ready" });
    // Both requests share the server's guard: one saying it is enough.
    expect(cataloguePageState(notReady, { ok: false })).toEqual({ kind: "not-ready" });
    expect(cataloguePageState({ ok: false }, notReady)).toEqual({ kind: "not-ready" });
  });

  it("has no page for a source that is gone", () => {
    const gone = { ok: false, code: "SOURCE_NOT_FOUND" };
    expect(cataloguePageState(gone, gone)).toEqual({ kind: "error", code: "SOURCE_NOT_FOUND" });
    expect(cataloguePageState(OK, gone)).toEqual({ kind: "error", code: "SOURCE_NOT_FOUND" });
  });

  it("shows the listing's code when both failed", () => {
    expect(
      cataloguePageState(
        { ok: false, code: "UNAUTHENTICATED" },
        { ok: false, code: "INTERNAL_ERROR" },
      ),
    ).toEqual({ kind: "error", code: "UNAUTHENTICATED" });
    expect(cataloguePageState({ ok: false }, { ok: false, code: "INTERNAL_ERROR" })).toEqual({
      kind: "error",
      code: "INTERNAL_ERROR",
    });
  });

  it("is unavailable when neither request got an answer", () => {
    expect(cataloguePageState({ ok: false }, { ok: false })).toEqual({ kind: "unavailable" });
  });

  it("ignores the code of a request that succeeded", () => {
    // Cannot happen with openapi-fetch, and must not matter if it ever does.
    expect(cataloguePageState({ ok: true, code: "SOURCE_NOT_READY" }, OK)).toEqual({
      kind: "ok",
      listingFailed: false,
      categoriesFailed: false,
    });
  });
});
