import { getTranslations } from "next-intl/server";
import { cn } from "@/lib/utils";

/**
 * The logotype: the word "lum" in Sora 600 and a gradient disc in place of the
 * "o" (docs/design/web-sprint-1.md, every artboard).
 *
 * Pure CSS and a Server Component, so it costs the marketing pages nothing —
 * no image request, no client JavaScript. The disc takes the brand gradient
 * from `bg-brand-orb` in globals.css and from nowhere else.
 *
 * The visible glyphs are hidden from assistive technology and the accessible
 * name is the brand name from the messages: "lum" followed by a decorative
 * circle is not what a screen reader should announce.
 */
export async function Wordmark({
  size = "md",
  className,
}: {
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const brand = await getTranslations("Brand");

  const word = { sm: "text-xl", md: "text-2xl", lg: "text-[26px]" }[size];
  const disc = { sm: "size-[11px] mt-[7px]", md: "size-[13px] mt-2", lg: "size-3.5 mt-[9px]" }[
    size
  ];

  return (
    <span className={cn("inline-flex items-center gap-0.5", className)}>
      <span className="sr-only">{brand("name")}</span>
      <span
        aria-hidden="true"
        className={cn("leading-none font-semibold tracking-tight", word)}
      >
        lum
      </span>
      <span aria-hidden="true" className={cn("bg-brand-orb inline-block rounded-full", disc)} />
    </span>
  );
}
