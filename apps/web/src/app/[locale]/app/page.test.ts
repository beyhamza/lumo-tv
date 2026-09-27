import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { HomeRails } from "@/lib/home/load-home-rails";

/**
 * The home page's EPG clock (I-3, S9-07-03).
 *
 * The defect this pins: the page passed `new Date()` to `loadHomeRails`, so
 * the `epgNow()` default the loader carries was dead in production and a
 * `LUMO_NOW`-controlled session moved the Guide but left "En ce moment" on the
 * real clock. The test drives the Server Component itself — no React rendering,
 * the component tree is never evaluated — and reads the instant the page hands
 * to the loader. It fails on `new Date()` because the pinned instant is far
 * from any real one.
 */
const { loadHomeRails, SOURCE } = vi.hoisted(() => ({
  loadHomeRails: vi.fn<(token: string, sourceId: string, now?: Date) => Promise<HomeRails>>(),
  SOURCE: {
    id: "33333333-3333-4333-8333-333333333333",
    label: "Banc",
    status: "READY",
    last_synced_at: "2026-09-26T00:00:00Z",
  },
}));

vi.mock("@/lib/home/load-home-rails", () => ({ loadHomeRails }));

vi.mock("next-intl/server", () => ({
  setRequestLocale: vi.fn(),
  getTranslations: vi.fn(async () => (key: string) => key),
  getTimeZone: vi.fn(async () => "Europe/Paris"),
}));

vi.mock("@/i18n/navigation", () => ({
  hrefFor: (_locale: string, href: string) => href,
}));

vi.mock("@/lib/session/session", () => ({
  requireSession: vi.fn(async () => ({
    accessToken: "token",
    userId: "11111111-1111-4111-8111-111111111111",
  })),
}));

vi.mock("@/lib/sources/active-source-store", () => ({
  loadActiveSource: vi.fn(async () => ({ state: "selected", source: SOURCE, sources: [SOURCE] })),
}));

// The rails below the page are not under test and are never rendered; making
// them no-ops keeps this a test of the page's own decisions.
vi.mock("@/components/app/ChannelRail", () => ({ ChannelRail: () => null }));
vi.mock("@/components/app/ContinueRail", () => ({ ContinueRail: () => null }));
vi.mock("@/components/app/SourceNotice", () => ({ SourceNotice: () => null }));
vi.mock("@/components/app/Unavailable", () => ({ Unavailable: () => null }));

import HomePage from "./page";

const EMPTY_RAILS: HomeRails = {
  continueWatching: [],
  favorites: [],
  recents: [],
  onAir: new Map(),
  failed: false,
};

const ORIGINAL_NOW = process.env.LUMO_NOW;

afterEach(() => {
  if (ORIGINAL_NOW === undefined) {
    delete process.env.LUMO_NOW;
  } else {
    process.env.LUMO_NOW = ORIGINAL_NOW;
  }
});

beforeEach(() => {
  loadHomeRails.mockReset();
  loadHomeRails.mockResolvedValue(EMPTY_RAILS);
});

async function render(): Promise<Date | undefined> {
  await HomePage({ params: Promise.resolve({ locale: "fr" }) } as never);
  return loadHomeRails.mock.calls[0]?.[2];
}

describe("HomePage and the EPG clock", () => {
  it("hands the loader the pinned LUMO_NOW instant, not new Date()", async () => {
    // Deliberately nowhere near the present: a page that ignored epgNow()
    // would hand over now and this would fail on the value, not on a flake.
    process.env.LUMO_NOW = "2001-01-01T00:00:00Z";

    const now = await render();

    expect(loadHomeRails).toHaveBeenCalledTimes(1);
    expect(now?.toISOString()).toBe("2001-01-01T00:00:00.000Z");
  });

  it("hands over the real clock when LUMO_NOW is unset", async () => {
    delete process.env.LUMO_NOW;
    const before = Date.now();

    const now = await render();

    const after = Date.now();
    expect(now?.getTime()).toBeGreaterThanOrEqual(before);
    expect(now?.getTime()).toBeLessThanOrEqual(after);
  });
});
