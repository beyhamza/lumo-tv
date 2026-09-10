import { describe, expect, it } from "vitest";
import { readingMinutes, wordCount, WORDS_PER_MINUTE } from "./reading-time";

describe("wordCount", () => {
  it("counts whitespace-separated words, whatever the whitespace", () => {
    expect(wordCount("un deux  trois\nquatre\ttrois")).toBe(5);
  });

  it("is zero for an empty or blank string", () => {
    expect(wordCount("")).toBe(0);
    expect(wordCount("   \n ")).toBe(0);
  });
});

describe("readingMinutes", () => {
  it("never says less than a minute", () => {
    expect(readingMinutes([""])).toBe(1);
    expect(readingMinutes(["quelques mots"])).toBe(1);
  });

  it("rounds up over the words-per-minute figure", () => {
    const words = (n: number) => Array.from({ length: n }, () => "mot").join(" ");
    expect(readingMinutes([words(WORDS_PER_MINUTE)])).toBe(1);
    expect(readingMinutes([words(WORDS_PER_MINUTE + 1)])).toBe(2);
    expect(readingMinutes([words(WORDS_PER_MINUTE), words(WORDS_PER_MINUTE)])).toBe(2);
  });
});
