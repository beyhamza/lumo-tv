import { parseLegalMarkdown, type LegalInline } from "@/lib/legal/markdown";

/**
 * A legal document's body, as React elements and never as an HTML string
 * (ADR 0015 §4). A Server Component, safe in the marketing zone.
 */
function Inlines({ inlines }: { inlines: LegalInline[] }) {
  return inlines.map((inline, index) => {
    if (inline.kind === "bold") {
      return (
        <strong key={index} className="text-foreground font-semibold">
          {inline.text}
        </strong>
      );
    }
    if (inline.kind === "link") {
      return (
        <a key={index} href={inline.href} rel="noopener noreferrer" className="text-foreground underline underline-offset-4">
          {inline.text}
        </a>
      );
    }
    return inline.text;
  });
}

export function LegalMarkdown({ source }: { source: string }) {
  return parseLegalMarkdown(source).map((block, index) => {
    if (block.kind === "heading") {
      const Heading = block.level === 2 ? "h2" : "h3";
      return (
        <Heading
          key={index}
          className={
            block.level === 2
              ? "mt-10 text-xl font-semibold tracking-tight"
              : "mt-6 text-base font-semibold tracking-tight"
          }
        >
          <Inlines inlines={block.inlines} />
        </Heading>
      );
    }
    if (block.kind === "list") {
      return (
        <ul key={index} className="text-muted-foreground mt-3 list-disc space-y-1 pl-6">
          {block.items.map((item, itemIndex) => (
            <li key={itemIndex}>
              <Inlines inlines={item} />
            </li>
          ))}
        </ul>
      );
    }
    return (
      <p key={index} className="text-muted-foreground mt-3 max-w-[70ch]">
        <Inlines inlines={block.inlines} />
      </p>
    );
  });
}
