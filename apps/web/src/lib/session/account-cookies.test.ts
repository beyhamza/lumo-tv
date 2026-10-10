import { describe, expect, it } from "vitest";
import { accountCookieNames, expireAccountCookies } from "./account-cookies";

/**
 * BUG-R020-01-02: when a session ends, the account's preference cookies go with
 * it — and nothing that belongs to the browser rather than the account.
 */
const ACCOUNT = "11111111-1111-1111-1111-111111111111";
const SOURCE = "22222222-2222-2222-2222-222222222222";

describe("account cookies", () => {
  it("picks the active-source and Direct-view cookies, whatever their key", () => {
    expect(
      accountCookieNames([
        `lumo_active_source_${ACCOUNT}`,
        `lumo_direct_view_${SOURCE}`,
        "lumo_session",
        "NEXT_LOCALE",
        "theme",
      ]),
    ).toEqual([`lumo_active_source_${ACCOUNT}`, `lumo_direct_view_${SOURCE}`]);
  });

  it("expires each one on the path it was written with", () => {
    const written: Array<{ name: string; value: string; maxAge: number; path: string }> = [];

    expireAccountCookies([`lumo_active_source_${ACCOUNT}`, "NEXT_LOCALE"], (name, value, options) =>
      written.push({ name, value, maxAge: options.maxAge, path: options.path }),
    );

    expect(written).toEqual([
      { name: `lumo_active_source_${ACCOUNT}`, value: "", maxAge: 0, path: "/" },
    ]);
  });

  it("does nothing when the browser holds none", () => {
    const written: string[] = [];
    expireAccountCookies(["lumo_session"], (name) => written.push(name));
    expect(written).toEqual([]);
  });
});
