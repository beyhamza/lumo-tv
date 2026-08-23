import { beforeAll, describe, expect, it } from "vitest";

/**
 * The session cookie.
 *
 * What is worth asserting is not "it round-trips" but the three failure modes
 * that decide whether someone stays signed in or gets signed out for nothing:
 * a tampered cookie is refused, a cookie sealed under another secret is refused,
 * and the staleness check leaves enough margin that a token does not expire
 * mid-flight.
 */
const SECRET = "test-secret-that-is-long-enough-to-be-accepted";

beforeAll(() => {
  process.env.SESSION_SECRET = SECRET;
});

const payload = {
  accessToken: "access-token",
  refreshToken: "refresh-token",
  accessTokenExpiresAt: 2_000_000_000,
  userId: "11111111-1111-1111-1111-111111111111",
  deviceId: "22222222-2222-2222-2222-222222222222",
  email: "someone@example.com",
};

describe("session cookie", () => {
  it("round-trips a session", async () => {
    const { sealSession, unsealSession } = await import("./cookie");

    const sealed = await sealSession(payload);
    expect(await unsealSession(sealed)).toMatchObject(payload);
  });

  it("does not carry the tokens in the clear", async () => {
    const { sealSession } = await import("./cookie");

    const sealed = await sealSession(payload);

    // Encrypted, not merely signed: a signed cookie is tamper-proof but
    // readable by anyone holding the cookie file.
    expect(sealed).not.toContain("refresh-token");
    expect(sealed).not.toContain("access-token");
    expect(sealed).not.toContain("someone@example.com");
  });

  it("refuses a tampered value", async () => {
    const { sealSession, unsealSession } = await import("./cookie");

    const sealed = await sealSession(payload);
    const tampered = sealed.slice(0, -2) + (sealed.endsWith("A") ? "B" : "A");

    expect(await unsealSession(tampered)).toBeNull();
  });

  it("refuses a value sealed under a different secret", async () => {
    const { sealSession, unsealSession } = await import("./cookie");
    const sealed = await sealSession(payload);

    // The key is derived on every call rather than cached at module load, so
    // rotating the secret takes effect immediately — which is also what makes
    // a secret rotation sign everyone out rather than half-work.
    process.env.SESSION_SECRET = "a-completely-different-secret-of-length";
    try {
      expect(await unsealSession(sealed)).toBeNull();
    } finally {
      process.env.SESSION_SECRET = SECRET;
    }
  });

  it("refuses nothing at all", async () => {
    const { unsealSession } = await import("./cookie");

    expect(await unsealSession(undefined)).toBeNull();
    expect(await unsealSession("")).toBeNull();
    expect(await unsealSession("not-a-jwe")).toBeNull();
  });

  it("treats a token expiring within the skew as stale", async () => {
    const { isAccessTokenStale, REFRESH_SKEW_SECONDS } = await import("./cookie");

    const now = 1_000_000;
    const expiringNow = { ...payload, accessTokenExpiresAt: now };
    const expiringInsideSkew = {
      ...payload,
      accessTokenExpiresAt: now + REFRESH_SKEW_SECONDS - 1,
    };
    const expiringLater = {
      ...payload,
      accessTokenExpiresAt: now + REFRESH_SKEW_SECONDS + 60,
    };

    // Without the skew, a token with four seconds left would be sent, arrive
    // expired, and fail for no reason the user could understand.
    expect(isAccessTokenStale(expiringNow, now)).toBe(true);
    expect(isAccessTokenStale(expiringInsideSkew, now)).toBe(true);
    expect(isAccessTokenStale(expiringLater, now)).toBe(false);
  });
});
