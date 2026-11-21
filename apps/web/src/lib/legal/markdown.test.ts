import { describe, expect, it } from "vitest";
import { legalPlaceholders, parseLegalInlines, parseLegalMarkdown } from "./markdown";

describe("parseLegalMarkdown", () => {
  it("reads headings, paragraphs and lists, in order", () => {
    const blocks = parseLegalMarkdown(
      "## Titre\n\nUne ligne\ncontinuée.\n\n- un\n- deux\n\n### Sous-titre\nFin.",
    );

    expect(blocks.map((block) => block.kind)).toEqual([
      "heading",
      "paragraph",
      "list",
      "heading",
      "paragraph",
    ]);
    expect(blocks[1]).toEqual({
      kind: "paragraph",
      inlines: [{ kind: "text", text: "Une ligne continuée." }],
    });
    expect(blocks[2]).toMatchObject({ kind: "list", items: [[{ text: "un" }], [{ text: "deux" }]] });
    expect(blocks[3]).toMatchObject({ kind: "heading", level: 3 });
  });

  it("keeps a single h1 on the page: a # in a body is a level 2", () => {
    expect(parseLegalMarkdown("# Titre")[0]).toMatchObject({ kind: "heading", level: 2 });
  });

  it("reads Windows line endings from a textarea", () => {
    expect(parseLegalMarkdown("## Titre\r\n\r\nTexte").map((block) => block.kind)).toEqual([
      "heading",
      "paragraph",
    ]);
  });

  it("shows HTML as text", () => {
    expect(parseLegalMarkdown("<script>alert(1)</script>")).toEqual([
      { kind: "paragraph", inlines: [{ kind: "text", text: "<script>alert(1)</script>" }] },
    ]);
  });
});

describe("parseLegalInlines", () => {
  it("reads bold and links between text", () => {
    expect(parseLegalInlines("Voir **ceci** et [le site](https://example.com) ici")).toEqual([
      { kind: "text", text: "Voir " },
      { kind: "bold", text: "ceci" },
      { kind: "text", text: " et " },
      { kind: "link", text: "le site", href: "https://example.com" },
      { kind: "text", text: " ici" },
    ]);
  });

  it("accepts mailto", () => {
    expect(parseLegalInlines("[écrire](mailto:legal@example.com)")).toEqual([
      { kind: "link", text: "écrire", href: "mailto:legal@example.com" },
    ]);
  });

  it.each(["javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,x", "http://example.com", "//example.com"])(
    "keeps the label and drops the target of %s",
    (href) => {
      expect(parseLegalInlines(`[cliquer](${href})`)).toEqual([{ kind: "text", text: "cliquer" }]);
    },
  );
});

describe("legalPlaceholders", () => {
  it("finds what is left to complete", () => {
    expect(legalPlaceholders("Responsable : [nom à compléter]. Hébergeur : [à compléter].")).toEqual([
      "[nom à compléter]",
      "[à compléter]",
    ]);
  });

  it("does not take a link for one", () => {
    expect(legalPlaceholders("Voir [le site](https://example.com).")).toEqual([]);
  });
});
