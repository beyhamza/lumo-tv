import { setRequestLocale } from "next-intl/server";
import { SiteFooter } from "@/components/site/SiteFooter";
import { SiteHeader } from "@/components/site/SiteHeader";
import { routing, type Locale } from "@/i18n/routing";

/**
 * The marketing zone: `/`, `/guides/*` (docs/architecture.md §4).
 *
 * Everything under here is prerendered at build time and revalidated on a
 * schedule — SSG with ISR. The rules that keep it that way, and that a reviewer
 * should enforce:
 *
 * - **No client component.** Not one. A single `"use client"` in this subtree
 *   adds a hydration bundle to a page whose target is LCP under two seconds.
 * - **No authenticated API call, and no `cookies()`.** Either would opt the
 *   route into dynamic rendering, and the page would stop being cacheable
 *   without anything failing to make that obvious.
 *
 * `next build` prints the proof: these routes must appear as `○` (prerendered),
 * never as `ƒ` (dynamic).
 */
export const revalidate = 3600;

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export default async function MarketingLayout({
  children,
  params,
}: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <>
      <SiteHeader locale={locale as Locale} />
      <main id="main" className="flex-1">
        {children}
      </main>
      <SiteFooter locale={locale as Locale} />
    </>
  );
}
