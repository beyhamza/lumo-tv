import { getTranslations } from "next-intl/server";
import { cn } from "@/lib/utils";

/**
 * The small, visible "[mock] missing data" label.
 *
 * Placed next to every value the mock-ups show and that, per
 * docs/design/api-gaps.md, should come from the server but does not reach this
 * page yet — a plan quota on a static marketing page, a section the API has no
 * controller for. The value is still displayed so the layout can be judged, and
 * the label is what keeps it from being mistaken for a fact.
 *
 * Translated like everything else, and a Server Component: it has to be usable
 * from the prerendered marketing zone.
 */
export async function MockBadge({ className }: { className?: string }) {
  const t = await getTranslations("Mock");

  return (
    <span
      className={cn(
        "border-destructive/50 text-destructive inline-flex items-center rounded-sm border border-dashed px-1.5 py-px font-mono text-[10px] leading-4 tracking-wide whitespace-nowrap",
        className,
      )}
    >
      {t("missingData")}
    </span>
  );
}
