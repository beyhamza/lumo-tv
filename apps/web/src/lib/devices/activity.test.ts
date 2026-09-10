import { describe, expect, it } from "vitest";
import { ACTIVE_WINDOW_MS, deviceActivity } from "./activity";

const now = new Date("2026-09-10T12:00:00Z");
const ago = (ms: number) => new Date(now.getTime() - ms).toISOString();

describe("deviceActivity", () => {
  it("is online for the device reading the page, whatever it was last seen", () => {
    expect(deviceActivity({ is_current: true, last_seen_at: ago(10 * ACTIVE_WINDOW_MS) }, now))
      .toEqual({ kind: "online" });
    expect(deviceActivity({ is_current: true, last_seen_at: null }, now)).toEqual({
      kind: "online",
    });
  });

  it("is active under the 24-hour threshold", () => {
    expect(deviceActivity({ is_current: false, last_seen_at: ago(60_000) }, now)).toEqual({
      kind: "active",
    });
    expect(
      deviceActivity({ is_current: false, last_seen_at: ago(ACTIVE_WINDOW_MS - 1) }, now),
    ).toEqual({ kind: "active" });
  });

  it("shows the date from 24 hours on", () => {
    const at = ago(3 * ACTIVE_WINDOW_MS);
    expect(deviceActivity({ is_current: false, last_seen_at: at }, now)).toEqual({
      kind: "seen",
      at: new Date(at),
    });
    expect(
      deviceActivity({ is_current: false, last_seen_at: ago(ACTIVE_WINDOW_MS) }, now).kind,
    ).toBe("seen");
  });

  it("says never for a device that has not called back, or an unreadable date", () => {
    expect(deviceActivity({ is_current: false, last_seen_at: null }, now)).toEqual({
      kind: "never",
    });
    expect(deviceActivity({ is_current: false }, now)).toEqual({ kind: "never" });
    expect(deviceActivity({ is_current: false, last_seen_at: "not a date" }, now)).toEqual({
      kind: "never",
    });
  });
});
