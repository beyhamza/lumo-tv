import { describe, expect, it } from "vitest";
import en from "./en.json";
import fr from "./fr.json";

/**
 * The two message catalogues have to stay the same shape.
 *
 * `AGENTS.md` §4 asks for FR and EN from the first screen, and the Android side
 * enforces it with a fatal `MissingTranslation` lint. The web had nothing: a key
 * added to `en.json` and forgotten in `fr.json` builds, deploys, and shows an
 * English string — or `App.devicesEmpty` — to a French visitor, on whichever
 * page nobody opened before release.
 *
 * This is the same guarantee, in the only place the web can make it: a test.
 * next-intl resolves keys at runtime, so nothing in the type system or the build
 * notices the difference.
 */
describe("message catalogues", () => {
  const enKeys = flatten(en);
  const frKeys = flatten(fr);

  it("carry exactly the same keys", () => {
    expect(missing(enKeys, frKeys)).toEqual([]);
    expect(missing(frKeys, enKeys)).toEqual([]);
  });

  it("have no empty or untranslated-looking value", () => {
    // A key copied over as a placeholder is worse than a missing one: the test
    // above passes, and the untranslated string ships.
    const blank = [...enKeys.entries(), ...frKeys.entries()]
      .filter(([, value]) => value.trim().length === 0)
      .map(([key]) => key);

    expect(blank).toEqual([]);
  });
});

/** Every leaf, as `Namespace.key`, mapped to its string. */
function flatten(source: unknown, prefix = ""): Map<string, string> {
  const out = new Map<string, string>();

  for (const [key, value] of Object.entries(source as Record<string, unknown>)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === "object") {
      for (const [nested, leaf] of flatten(value, path)) out.set(nested, leaf);
    } else {
      out.set(path, String(value));
    }
  }

  return out;
}

function missing(from: Map<string, string>, other: Map<string, string>): string[] {
  return [...from.keys()].filter((key) => !other.has(key)).sort();
}
