import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionPayload } from "./cookie";

/**
 * S10B-05: a request still carrying a token that was just rotated must not
 * spend it again — the server would read that as theft and revoke the device.
 */
const session = (refreshToken: string): SessionPayload => ({
  accessToken: "access-old",
  refreshToken,
  accessTokenExpiresAt: 0,
  userId: "11111111-1111-1111-1111-111111111111",
  deviceId: "22222222-2222-2222-2222-222222222222",
  email: "someone@example.com",
});

function tokenPair(n: number): Response {
  return new Response(
    JSON.stringify({ access_token: `access-${n}`, refresh_token: `refresh-${n}`, expires_in: 900 }),
    { status: 200, headers: { "content-type": "application/json" } },
  );
}

describe("refreshSession", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.resetModules();
    let n = 0;
    fetchMock = vi.fn(async () => tokenPair(++n));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("deduplicates two refreshes in flight", async () => {
    const { refreshSession } = await import("./refresh");

    const [a, b] = await Promise.all([
      refreshSession(session("refresh-0")),
      refreshSession(session("refresh-0")),
    ]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a).toEqual(b);
  });

  it("hands a finished rotation to a late request with the old token, without spending it again", async () => {
    const { refreshSession } = await import("./refresh");

    const first = await refreshSession(session("refresh-0"));
    const late = await refreshSession(session("refresh-0"));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(late).toEqual(first);
    expect(late.status === "rotated" && late.session.refreshToken).toBe("refresh-1");
  });

  it("forgets the rotation once the grace window has passed", async () => {
    const { refreshSession, ROTATION_GRACE_MS } = await import("./refresh");
    let now = 1_000_000;
    const clock = () => now;

    await refreshSession(session("refresh-0"), clock);
    now += ROTATION_GRACE_MS + 1;
    await refreshSession(session("refresh-0"), clock);

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not remember a rejection: the next attempt asks the server again", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 401 }));
    const { refreshSession } = await import("./refresh");

    expect((await refreshSession(session("refresh-0"))).status).toBe("rejected");
    await refreshSession(session("refresh-0"));

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
