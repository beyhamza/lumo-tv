/**
 * "N min de lecture" for a guide (W2, docs/design/web-sprint-1.md).
 *
 * Derived from the content at build time — the guides are prerendered, so this
 * runs once per page per build and never in a browser. Two hundred words a
 * minute is the usual figure for prose; the exact value matters less than the
 * fact that it is the same on every guide.
 *
 * Never below one minute: "0 min de lecture" reads as a bug, not as short.
 */
export const WORDS_PER_MINUTE = 200;

export function wordCount(text: string): number {
  const trimmed = text.trim();
  return trimmed.length === 0 ? 0 : trimmed.split(/\s+/).length;
}

export function readingMinutes(parts: readonly string[]): number {
  const words = parts.reduce((total, part) => total + wordCount(part), 0);
  return Math.max(1, Math.ceil(words / WORDS_PER_MINUTE));
}
