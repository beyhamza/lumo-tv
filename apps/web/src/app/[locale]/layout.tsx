import type { Metadata } from "next";
import { Sora, Space_Mono } from "next/font/google";
import { hasLocale } from "next-intl";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { routing } from "@/i18n/routing";
import { siteUrl } from "@/lib/env.public";
import "../globals.css";

// Sora pour tout, Space Mono pour les métadonnées techniques (docs/design/design-system.md).
// Les deux passent par next/font : les fichiers sont servis depuis notre origine,
// donc aucune requête vers Google au chargement — et aucun décalage de mise en
// page quand la police arrive.
const sora = Sora({ variable: "--font-sora", subsets: ["latin"], display: "swap" });
const spaceMono = Space_Mono({
  variable: "--font-space-mono",
  subsets: ["latin"],
  weight: ["400", "700"],
  display: "swap",
});

/**
 * The root layout, under the `[locale]` segment.
 *
 * There is no `app/layout.tsx` above this one, which Next.js 16 explicitly
 * supports for internationalised apps: a layout under a dynamic segment is the
 * root layout, and the segment becomes a root parameter. The alternative — a
 * pass-through root that returns `children` — exists only to work around a
 * limitation that no longer applies.
 *
 * `generateStaticParams` is what makes both language trees prerenderable;
 * `setRequestLocale` is what keeps them that way. Without the latter, next-intl
 * reads the locale from the request at render time, every page becomes dynamic,
 * and the marketing zone quietly stops being static — with no error to notice.
 */
export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Brand" });

  return {
    // A template, so each page supplies only its own title.
    title: { default: t("name"), template: `%s — ${t("name")}` },
    description: t("tagline"),
    // Makes every relative URL in a page's metadata absolute, which Open Graph
    // and canonical tags require.
    metadataBase: new URL(siteUrl()),
    applicationName: t("name"),
    formatDetection: { telephone: false },
  };
}

export default async function LocaleLayout({
  children,
  params,
}: LayoutProps<"/[locale]">) {
  const { locale } = await params;

  // An unknown locale is a 404, not a silent fallback: /de/guides must not
  // return a French page under a German URL, which would be indexed as such.
  if (!hasLocale(routing.locales, locale)) notFound();

  setRequestLocale(locale);

  return (
    <html lang={locale} className={`${sora.variable} ${spaceMono.variable} h-full`}>
      <body className="bg-background text-foreground flex min-h-full flex-col antialiased">
        {/* NextIntlClientProvider is NOT here on purpose. It is a client
            component, and mounting it at the root would put a client boundary —
            and every message of the active locale — on the marketing pages,
            which must ship no client JavaScript at all. Each zone that actually
            has interactive components mounts its own provider with only the
            namespaces it needs. */}
        {children}
      </body>
    </html>
  );
}

