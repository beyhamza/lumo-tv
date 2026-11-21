/**
 * The Markdown of a legal document (ADR 0015 §4): `##` and `###` headings, `-`
 * lists, `**bold**` and `[links](https://…)`. Nothing else.
 *
 * Parsed into data and rendered as React elements (`LegalMarkdown`), never into
 * an HTML string: a tag typed in a body is shown as text. Those pages are public
 * and an administrator's account can be stolen, so a link that is not `https:`
 * or `mailto:` keeps its label and loses its target.
 */
export type LegalInline =
  | { kind: "text"; text: string }
  | { kind: "bold"; text: string }
  | { kind: "link"; text: string; href: string };

export type LegalBlock =
  | { kind: "heading"; level: 2 | 3; inlines: LegalInline[] }
  | { kind: "paragraph"; inlines: LegalInline[] }
  | { kind: "list"; items: LegalInline[][] };

const HEADING = /^(#{1,3})\s+(.+)$/;
const LIST_ITEM = /^[-*]\s+(.+)$/;
const INLINE = /\*\*(.+?)\*\*|\[([^\]\n]+)\]\(((?:[^()\s]|\([^()\s]*\))+)\)/g;
const SAFE_LINK = /^(https:\/\/|mailto:)/i;

export function parseLegalInlines(line: string): LegalInline[] {
  const inlines: LegalInline[] = [];
  let cursor = 0;
  for (const match of line.matchAll(INLINE)) {
    if (match.index > cursor) {
      inlines.push({ kind: "text", text: line.slice(cursor, match.index) });
    }
    const [whole, bold, label, href] = match;
    if (bold !== undefined) {
      inlines.push({ kind: "bold", text: bold });
    } else if (SAFE_LINK.test(href)) {
      inlines.push({ kind: "link", text: label, href });
    } else {
      inlines.push({ kind: "text", text: label });
    }
    cursor = match.index + whole.length;
  }
  if (cursor < line.length) inlines.push({ kind: "text", text: line.slice(cursor) });
  return inlines;
}

export function parseLegalMarkdown(source: string): LegalBlock[] {
  const blocks: LegalBlock[] = [];
  let paragraph: string[] = [];
  let items: LegalInline[][] = [];

  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ kind: "paragraph", inlines: parseLegalInlines(paragraph.join(" ")) });
      paragraph = [];
    }
    if (items.length > 0) {
      blocks.push({ kind: "list", items });
      items = [];
    }
  };

  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    const heading = HEADING.exec(line);
    const item = LIST_ITEM.exec(line);

    if (!line) {
      flush();
    } else if (heading) {
      flush();
      // The page's title is the `<h1>`: a `#` in a body is one level below it.
      blocks.push({
        kind: "heading",
        level: heading[1].length === 3 ? 3 : 2,
        inlines: parseLegalInlines(heading[2]),
      });
    } else if (item) {
      if (paragraph.length > 0) flush();
      items.push(parseLegalInlines(item[1]));
    } else {
      if (items.length > 0) flush();
      paragraph.push(line);
    }
  }
  flush();
  return blocks;
}

/**
 * The `[to complete]` left in a text: brackets that do not open a link.
 *
 * The web refuses to publish them. A policy with a hole where the data
 * controller's name belongs would otherwise go public on one click.
 */
export function legalPlaceholders(source: string): string[] {
  return Array.from(source.matchAll(/\[[^\]\n]+\](?!\()/g), (match) => match[0]);
}
