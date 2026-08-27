import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import fr from "@/messages/fr.json";
import { GOOGLE_ERROR_CODES, googleErrorCode } from "./google";

/**
 * The narrowing that stands between a cross-site POST and what the sign-in page
 * says.
 *
 * `/api/auth/google` is reached by a form Google submits from another origin, and
 * whatever it could not complete comes back as `?google=…` on the sign-in page.
 * If that parameter reached `getTranslations` unchecked, a stranger would be
 * choosing which of this application's messages a visitor reads — and next-intl
 * throws on a key that does not exist, so the page would not merely say the wrong
 * thing, it would fail to render.
 */
describe("googleErrorCode", () => {
  it("accepts every code the route handler can send", () => {
    // Enumerated rather than spot-checked: a code added to the list and not to
    // the message catalogues is the failure this guards, and it would otherwise
    // be found on the one page nobody opens before release.
    for (const code of GOOGLE_ERROR_CODES) {
      expect(googleErrorCode(code)).toBe(code);
    }
  });


  it("has a sentence in both languages for every one of them", () => {
    // The route handler picks one of these and the sign-in page renders it
    // through `Errors`. next-intl resolves keys at runtime, so a code with no
    // message is not a build error — it is a page that throws, in one language,
    // on the path nobody walks before release.
    for (const code of GOOGLE_ERROR_CODES) {
      const key = code === "NETWORK" ? "network" : code === "GENERIC" ? "generic" : code;
      expect(en.Errors, `en: ${key}`).toHaveProperty(key);
      expect(fr.Errors, `fr: ${key}`).toHaveProperty(key);
    }
  });

  it("refuses a message key that is not one of them", () => {
    // A real key in another namespace is the interesting case: it exists, so a
    // naive check for "does this translate" would let it through.
    expect(googleErrorCode("INVALID_CREDENTIALS")).toBeNull();
    expect(googleErrorCode("generic")).toBeNull();
  });

  it("refuses anything that is not a string", () => {
    // Next hands repeated query parameters over as an array, and an absent one
    // as undefined.
    expect(googleErrorCode(["GENERIC", "NETWORK"])).toBeNull();
    expect(googleErrorCode(undefined)).toBeNull();
    expect(googleErrorCode(null)).toBeNull();
    expect(googleErrorCode(42)).toBeNull();
  });

  it("does not fall for a near miss", () => {
    expect(googleErrorCode("GENERIC ")).toBeNull();
    expect(googleErrorCode("generic")).toBeNull();
    expect(googleErrorCode("")).toBeNull();
  });
});
