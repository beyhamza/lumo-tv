import { describe, expect, it } from "vitest";
import { explicitLocale, localeForUnprefixed } from "./interface-locale";

describe("the interface language chosen in Settings (S13-04)", () => {
  it("accepts only a supported locale", () => {
    expect(explicitLocale("en")).toBe("en");
    expect(explicitLocale("fr")).toBe("fr");
    for (const value of ["de", "EN", "", null, undefined, ["en"]]) {
      expect(explicitLocale(value)).toBeNull();
    }
  });

  it("decides an unprefixed URL", () => {
    expect(localeForUnprefixed("/", "en")).toBe("en");
    expect(localeForUnprefixed("/app", "en")).toBe("en");
    expect(localeForUnprefixed("/login", "fr")).toBe("fr");
  });

  it("never overrides a URL that names its language", () => {
    expect(localeForUnprefixed("/fr", "en")).toBeNull();
    expect(localeForUnprefixed("/fr/app/settings/application", "en")).toBeNull();
    expect(localeForUnprefixed("/en/guides", "fr")).toBeNull();
  });

  it("does nothing without a valid remembered choice", () => {
    expect(localeForUnprefixed("/", undefined)).toBeNull();
    expect(localeForUnprefixed("/", "de")).toBeNull();
  });
});
