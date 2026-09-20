import { describe, expect, it } from "vitest";
import { sourceCondition } from "./source-condition";

const YESTERDAY = "2026-09-19T03:00:00Z";

/**
 * What a screen says about a source, and whether it still shows a catalogue
 * under it (US-017, US-024, contract lot C4). Shared by the home page, the three
 * catalogue pages and the source page.
 *
 * The rows that matter are the ones where `status` and "has a catalogue"
 * disagree: reading one off the other empties the home page every night.
 */
describe("sourceCondition", () => {
  it("says nothing about a ready source", () => {
    expect(sourceCondition({ status: "READY", last_synced_at: YESTERDAY })).toEqual({
      notice: null,
      browsable: true,
      stale: false,
    });
  });

  it("keeps the rails of a source that is synchronising again", () => {
    expect(sourceCondition({ status: "SYNCING", last_synced_at: YESTERDAY })).toEqual({
      notice: "syncing",
      browsable: true,
      stale: false,
    });
  });

  it("has nothing to browse during a first import", () => {
    expect(sourceCondition({ status: "SYNCING", last_synced_at: null })).toEqual({
      notice: "syncing",
      browsable: false,
      stale: false,
    });
    expect(sourceCondition({ status: "PENDING", last_synced_at: null })).toEqual({
      notice: "syncing",
      browsable: false,
      stale: false,
    });
  });

  it("treats a queued re-synchronisation like a running one", () => {
    expect(sourceCondition({ status: "PENDING", last_synced_at: YESTERDAY })).toEqual({
      notice: "syncing",
      browsable: true,
      stale: false,
    });
  });

  it("keeps the previous catalogue of a source in error", () => {
    expect(sourceCondition({ status: "ERROR", last_synced_at: YESTERDAY })).toEqual({
      notice: "error",
      browsable: true,
      stale: true,
    });
  });

  it("has nothing to browse when the first import failed", () => {
    expect(sourceCondition({ status: "ERROR", last_synced_at: null })).toEqual({
      notice: "error",
      browsable: false,
      stale: false,
    });
  });

  it("calls a catalogue possibly out of date only after a failure that left one", () => {
    // The sentence "this catalogue may be out of date" (US-024): true for a
    // failed refresh over a previous catalogue, and for nothing else.
    expect(sourceCondition({ status: "ERROR", last_synced_at: YESTERDAY }).stale).toBe(true);
    expect(sourceCondition({ status: "ERROR", last_synced_at: null }).stale).toBe(false);
    expect(sourceCondition({ status: "SYNCING", last_synced_at: YESTERDAY }).stale).toBe(false);
    expect(sourceCondition({ status: "READY", last_synced_at: YESTERDAY }).stale).toBe(false);
  });

  it("does not depend on a ready source carrying its date", () => {
    expect(sourceCondition({ status: "READY", last_synced_at: undefined })).toEqual({
      notice: null,
      browsable: true,
      stale: false,
    });
  });
});
