import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { guideSlugs, guideUpdatedAt, isGuideSlug } from "@/content/guides";
import { hrefFor } from "@/i18n/navigation";
import { routing, type Locale } from "@/i18n/routing";
import { JsonLd, articleJsonLd, breadcrumbJsonLd } from "@/lib/seo/json-ld";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * Every guide, in every locale, prerendered at build time.
 *
 * The cross product is deliberate: with `dynamicParams` left at its default, an
 * unknown slug still renders on demand and 404s, but the pages that exist are
 * static files a CDN can serve without ever waking the application.
 */
export function generateStaticParams() {
  return routing.locales.flatMap((locale) =>
    guideSlugs.map((slug) => ({ locale, slug })),
  );
}

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/guides/[slug]">): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isGuideSlug(slug)) return {};

  const t = await getTranslations({ locale, namespace: "Guides" });

  return pageMetadata({
    locale: locale as Locale,
    href: `/guides/${slug}`,
    title: t(`${slug}.title`),
    description: t(`${slug}.description`),
  });
}

export default async function GuidePage({
  params,
}: PageProps<"/[locale]/guides/[slug]">) {
  const { locale, slug } = await params;
  setRequestLocale(locale);

  if (!isGuideSlug(slug)) notFound();

  const t = await getTranslations("Guides");
  const nav = await getTranslations("Nav");
  const format = await getFormatter();
  const updated = new Date(guideUpdatedAt[slug]);

  const sections = [
    ["sectionOneTitle", "sectionOneBody"],
    ["sectionTwoTitle", "sectionTwoBody"],
    ["sectionThreeTitle", "sectionThreeBody"],
  ] as const;

  return (
    <>
      <JsonLd
        data={articleJsonLd({
          title: t(`${slug}.title`),
          description: t(`${slug}.description`),
          path: `/${locale}/guides/${slug}`,
          locale: locale as Locale,
          updatedIso: guideUpdatedAt[slug],
        })}
      />
      <JsonLd
        data={breadcrumbJsonLd([
          { name: nav("home"), path: `/${locale}` },
          { name: t("indexTitle"), path: `/${locale}/guides` },
          { name: t(`${slug}.title`), path: `/${locale}/guides/${slug}` },
        ])}
      />

      <article className="mx-auto w-full max-w-3xl px-4 py-16">
        <a
          href={hrefFor(locale as Locale, "/guides")}
          className="text-muted-foreground text-sm underline-offset-4 hover:underline"
        >
          ← {t("backToGuides")}
        </a>

        <h1 className="mt-6 text-3xl font-semibold tracking-tight text-balance">
          {t(`${slug}.title`)}
        </h1>

        <p className="text-muted-foreground mt-2 text-sm">
          {t("updatedOn", {
            date: format.dateTime(updated, { dateStyle: "long" }),
          })}
        </p>

        <p className="mt-8 text-lg text-pretty">{t(`${slug}.intro`)}</p>

        {sections.map(([title, body]) => (
          <section key={title} className="mt-10">
            <h2 className="text-xl font-semibold tracking-tight">
              {t(`${slug}.${title}`)}
            </h2>
            <p className="text-muted-foreground mt-3 text-pretty">
              {t(`${slug}.${body}`)}
            </p>
          </section>
        ))}
      </article>
    </>
  );
}
