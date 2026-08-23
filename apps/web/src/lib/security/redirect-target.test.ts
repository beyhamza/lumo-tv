import { describe, expect, it } from "vitest";
import { safeRedirectTarget } from "./redirect-target";

const LOCALES = ["fr", "en"] as const;

/**
 * The open-redirect guard.
 *
 * This runs on the page where someone has just typed their password, so the
 * cases below are the ones an attacker actually tries — not a survey of string
 * shapes.
 */
describe("safeRedirectTarget", () => {
  it("keeps an in-site path and strips the locale prefix", () => {
    expect(safeRedirectTarget("/fr/app/sources", LOCALES)).toBe("/app/sources");
    expect(safeRedirectTarget("/en/app/devices", LOCALES)).toBe("/app/devices");
    expect(safeRedirectTarget("/app", LOCALES)).toBe("/app");
  });

  it("keeps a query string", () => {
    expect(safeRedirectTarget("/fr/activate?code=ABCD1234", LOCALES)).toBe(
      "/activate?code=ABCD1234",
    );
  });

  it("refuses an absolute URL to another origin", () => {
    expect(safeRedirectTarget("https://evil.example", LOCALES)).toBe("/app");
    expect(safeRedirectTarget("http://evil.example/fr/app", LOCALES)).toBe("/app");
  });

  it("refuses a protocol-relative URL", () => {
    // The browser reads `//evil.example` as a host, not as a path — the classic
    // way past a check that only looks for a leading slash.
    expect(safeRedirectTarget("//evil.example", LOCALES)).toBe("/app");
    expect(safeRedirectTarget("//evil.example/fr/app", LOCALES)).toBe("/app");
  });

  it("refuses a backslash, which several browsers treat as a slash", () => {
    expect(safeRedirectTarget("/\\evil.example", LOCALES)).toBe("/app");
    expect(safeRedirectTarget("/app\\..\\..", LOCALES)).toBe("/app");
  });

  it("refuses control characters", () => {
    const withNewline = "/app" + String.fromCharCode(10) + "Set-Cookie: x=1";
    expect(safeRedirectTarget(withNewline, LOCALES)).toBe("/app");
    expect(safeRedirectTarget("/app" + String.fromCharCode(0), LOCALES)).toBe("/app");
  });

  it("falls back for anything that is not a string", () => {
    expect(safeRedirectTarget(undefined, LOCALES)).toBe("/app");
    expect(safeRedirectTarget(null, LOCALES)).toBe("/app");
    expect(safeRedirectTarget(42, LOCALES)).toBe("/app");
  });

  it("falls back when stripping the locale leaves nothing", () => {
    // `/fr` alone would become "", which is not a route.
    expect(safeRedirectTarget("/fr", LOCALES)).toBe("/app");
  });

  it("does not strip a path that merely starts with locale letters", () => {
    expect(safeRedirectTarget("/frais/app", LOCALES)).toBe("/frais/app");
  });
});
