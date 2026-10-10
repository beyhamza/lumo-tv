import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionPayload } from "./cookie";
import type { RefreshResult } from "./refresh";

/**
 * S10B-05: a Route Handler under `/api` refreshes a stale session itself, since
 * `proxy.ts` never runs there.
 */
const jar = new Map<string, { value: string; maxAge?: number }>();
const refreshSession = vi.fn<(s: SessionPayload) => Promise<RefreshResult>>();

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (jar.has(name) ? { name, value: jar.get(name)!.value } : undefined),
    getAll: () => [...jar.entries()].map(([name, { value }]) => ({ name, value })),
    set: (name: string, value: string, options: { maxAge?: number }) => {
      jar.set(name, { value, maxAge: options.maxAge });
    },
  }),
}));
vi.mock("./refresh", () => ({ refreshSession: (s: SessionPayload) => refreshSession(s) }));

const nowSeconds = () => Math.floor(Date.now() / 1000);

const base: SessionPayload = {
  accessToken: "access-old",
  refreshToken: "refresh-old",
  accessTokenExpiresAt: 0,
  userId: "11111111-1111-1111-1111-111111111111",
  deviceId: "22222222-2222-2222-2222-222222222222",
  email: "someone@example.com",
};

beforeAll(() => {
  process.env.SESSION_SECRET = "test-secret-that-is-long-enough-to-be-accepted";
});

async function storeSession(session: SessionPayload) {
  const { sealSession, sessionCookieOptions } = await import("./cookie");
  jar.set(sessionCookieOptions().name, { value: await sealSession(session) });
}

async function cookieName() {
  const { sessionCookieOptions } = await import("./cookie");
  return sessionCookieOptions().name;
}

describe("getRouteSession", () => {
  beforeEach(() => {
    jar.clear();
    refreshSession.mockReset();
  });

  it("returns a fresh session untouched, without refreshing", async () => {
    const { getRouteSession } = await import("./route-session");
    await storeSession({ ...base, accessTokenExpiresAt: nowSeconds() + 3600 });

    expect((await getRouteSession())?.accessToken).toBe("access-old");
    expect(refreshSession).not.toHaveBeenCalled();
  });

  it("refreshes a stale session, uses the new token and writes the new cookie", async () => {
    const { getRouteSession } = await import("./route-session");
    const { unsealSession } = await import("./cookie");
    await storeSession(base);
    const rotated = { ...base, accessToken: "access-new", refreshToken: "refresh-new" };
    refreshSession.mockResolvedValue({ status: "rotated", session: rotated });

    const session = await getRouteSession();

    expect(session?.accessToken).toBe("access-new");
    expect((await unsealSession(jar.get(await cookieName())!.value))?.refreshToken).toBe("refresh-new");
  });

  it("clears the cookie and answers no session when the refresh is rejected", async () => {
    const { getRouteSession } = await import("./route-session");
    await storeSession(base);
    refreshSession.mockResolvedValue({ status: "rejected" });

    expect(await getRouteSession()).toBeNull();
    expect(jar.get(await cookieName())).toEqual({ value: "", maxAge: 0 });
  });

  it("takes the account's preference cookies with a rejected session (BUG-R020-01-02)", async () => {
    const { getRouteSession } = await import("./route-session");
    await storeSession(base);
    jar.set(`lumo_active_source_${base.userId}`, { value: "33333333-3333-3333-3333-333333333333" });
    jar.set("NEXT_LOCALE", { value: "fr" });
    refreshSession.mockResolvedValue({ status: "rejected" });

    await getRouteSession();

    expect(jar.get(`lumo_active_source_${base.userId}`)).toEqual({ value: "", maxAge: 0 });
    expect(jar.get("NEXT_LOCALE")).toEqual({ value: "fr" });
  });

  it("keeps the session when the API is unavailable", async () => {
    const { getRouteSession } = await import("./route-session");
    await storeSession(base);
    refreshSession.mockResolvedValue({ status: "unavailable" });

    expect((await getRouteSession())?.refreshToken).toBe("refresh-old");
  });
});
