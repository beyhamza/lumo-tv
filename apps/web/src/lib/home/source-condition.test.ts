import { describe, expect, it } from "vitest";
import { sourceCondition } from "./source-condition";

const YESTERDAY = "2026-09-19T03:00:00Z";

/**
 * What the home page says about the active source, and whether it still shows
 * rails under it (US-017, contract lot C4).
 *
 * The rows that matter are the ones where `status` and "has a catalogue"
 * disagree: reading one off the other empties the home page every night.
 */
describe("sourceCondition", () => {
  it("says nothing about a ready source", () => {
    expect(sourceCondition({ status: "READY", last_synced_at: YESTERDAY })).toEqual({
      notice: null,
      browsable: true,
    });
  });

  it("keeps the rails of a source that is synchronising again", () => {
    expect(sourceCondition({ status: "SYNCING", last_synced_at: YESTERDAY })).toEqual({
      notice: "syncing",
      browsable: true,
    });
  });

  it("has nothing to browse during a first import", () => {
    expect(sourceCondition({ status: "SYNCING", last_synced_at: null })).toEqual({
      notice: "syncing",
      browsable: false,
    });
    expect(sourceCondition({ status: "PENDING", last_synced_at: null })).toEqual({
      notice: "syncing",
      browsable: false,
    });
  });

  it("treats a queued re-synchronisation like a running one", () => {
    expect(sourceCondition({ status: "PENDING", last_synced_at: YESTERDAY })).toEqual({
      notice: "syncing",
      browsable: true,
    });
  });

  it("keeps the previous catalogue of a source in error", () => {
    expect(sourceCondition({ status: "ERROR", last_synced_at: YESTERDAY })).toEqual({
      notice: "error",
      browsable: true,
    });
  });

  it("has nothing to browse when the first import failed", () => {
    expect(sourceCondition({ status: "ERROR", last_synced_at: null })).toEqual({
      notice: "error",
      browsable: false,
    });
  });

  it("does not depend on a ready source carrying its date", () => {
    expect(sourceCondition({ status: "READY", last_synced_at: undefined })).toEqual({
      notice: null,
      browsable: true,
    });
  });
});
