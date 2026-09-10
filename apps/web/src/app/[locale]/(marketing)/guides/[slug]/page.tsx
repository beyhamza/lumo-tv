import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import { guideSlugs, guideUpdatedAt, isGuideSlug } from "@/content/guides";
import { hrefFor } from "@/i18n/navigation";
import { routing, type Locale } from "@/i18n/routing";
import { readingMinutes } from "@/lib/guides/reading-time";
import { JsonLd, articleJsonLd, breadcrumbJsonLd } from "@/lib/seo/json-ld";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * A guide (W2, docs/design/web-sprint-1.md): the SEO engine, so readability
 * comes before everything else.
 *
 * Every guide, in every locale, prerendered at build time. The cross product is
 * deliberate: with `dynamicParams` left at its default, an unknown slug still
 * renders on demand and 404s, but the pages that exist are static files a CDN
 * can serve without ever waking the application.
 *
 * The layout is the mock-up's: a sticky table of contents on the left (240 px),
 * a reading column capped at 68 characters, 17 px, weight 300, line height
 * 1.7. The table of contents, the reading time and the previous / next links
 * are all derived from the content at build — none of it is an API need.
 *
 * Without JavaScript nothing tracks the scroll position, so the "current"
 * entry of the table of contents is the first one: where the page opens. It
 * is marked by a 2 px bar and not by colour alone.
 *
 * The figure is a hatched placeholder, like the product shot on the landing
 * page: a real screenshot would have to come from a royalty-free test set
 * (AGENTS.md §1).
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

const sections = [
  ["sectionOneTitle", "sectionOneBody"],
  ["sectionTwoTitle", "sectionTwoBody"],
  ["sectionThreeTitle", "sectionThreeBody"],
] as const;

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

  const hasCode = t.has(`${slug}.codeExample`);
  const minutes = readingMinutes([
    t(`${slug}.intro`),
    ...sections.flatMap(([title, body]) => [t(`${slug}.${title}`), t(`${slug}.${body}`)]),
    t(`${slug}.callout`),
  ]);

  const index = guideSlugs.indexOf(slug);
  const previous = index > 0 ? guideSlugs[index - 1] : null;
  const next = index < guideSlugs.length - 1 ? guideSlugs[index + 1] : null;

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

      <div className="mx-auto flex w-full max-w-7xl flex-col gap-10 px-6 py-14 sm:px-10 lg:flex-row lg:gap-[72px] lg:px-16">
        <nav
          aria-label={t("tocTitle")}
          className="w-full flex-none lg:sticky lg:top-8 lg:w-[240px] lg:self-start"
        >
          <p className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase">
            {t("tocTitle")}
          </p>
          <ol className="mt-4 flex flex-col gap-3 text-[13px]">
            {sections.map(([title], i) => (
              <li key={title}>
                <a
                  href={`#section-${i + 1}`}
                  aria-current={i === 0 ? "location" : undefined}
                  className={
                    i === 0
                      ? "text-secondary-foreground border-secondary-foreground block border-l-2 pl-3 font-semibold"
                      : "text-muted-foreground hover:text-foreground block pl-3.5"
                  }
                >
                  {t(`${slug}.${title}`)}
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <article className="w-full max-w-[68ch] min-w-0">
          <nav aria-label={t("breadcrumbLabel")} className="text-muted-foreground/80 text-xs">
            <a href={hrefFor(locale as Locale, "/guides")} className="hover:text-foreground">
              {t("indexTitle")}
            </a>
            <span aria-hidden="true"> &nbsp;›&nbsp; </span>
            <span>{t("breadcrumbSection")}</span>
          </nav>

          <h1 className="mt-4 text-3xl leading-[1.15] font-semibold tracking-[-0.025em] text-balance sm:text-[38px]">
            {t(`${slug}.title`)}
          </h1>

          <p className="text-muted-foreground/80 mt-4 flex flex-wrap items-center gap-x-4 text-[13px]">
            <span>
              {t("updatedIn", {
                date: format.dateTime(updated, { month: "long", year: "numeric" }),
              })}
            </span>
            <span aria-hidden="true">·</span>
            <span>{t("readingTime", { minutes })}</span>
          </p>

          <p className="mt-7 text-[17px] leading-[1.7] font-light text-pretty">
            {t(`${slug}.intro`)}
          </p>

          {sections.map(([title, body], i) => (
            <section key={title} id={`section-${i + 1}`} className="scroll-mt-8">
              <h2 className="mt-9 text-2xl font-semibold tracking-tight">
                {t(`${slug}.${title}`)}
              </h2>
              <p className="mt-3.5 text-[17px] leading-[1.7] font-light text-pretty">
                {t(`${slug}.${body}`)}
              </p>

              {/* The example URL is an example domain, never a real panel. */}
              {i === 0 && hasCode ? (
                <pre className="border-border bg-card text-muted-foreground mt-5 overflow-x-auto rounded-[14px] border px-5 py-4 font-mono text-sm">
                  <code>{t(`${slug}.codeExample`)}</code>
                </pre>
              ) : null}

              {i === 0 ? (
                <aside className="bg-secondary mt-6 flex gap-3.5 rounded-[14px] px-6 py-5">
                  <span aria-hidden="true" className="text-secondary-foreground font-bold">
                    →
                  </span>
                  <p className="text-[15px] leading-[1.6] font-light">{t(`${slug}.callout`)}</p>
                </aside>
              ) : null}

              {i === 1 ? (
                <figure
                  aria-hidden="true"
                  className="dark bg-background bg-placeholder-hatch text-muted-foreground/70 mt-6 flex h-[280px] items-center justify-center rounded-2xl font-mono text-xs"
                >
                  {t("figurePlaceholder")}
                </figure>
              ) : null}
            </section>
          ))}

          <nav
            aria-label={t("pagerLabel")}
            className="border-border mt-12 grid gap-4 border-t pt-7 sm:grid-cols-2"
          >
            {previous ? (
              <a
                href={hrefFor(locale as Locale, `/guides/${previous}`)}
                className="border-border bg-card hover:border-muted-foreground/40 rounded-[14px] border p-[18px] transition-colors"
              >
                <span className="text-muted-foreground/80 block text-[11px] tracking-[0.08em] uppercase">
                  ← {t("previous")}
                </span>
                <span className="mt-1.5 block text-sm font-semibold">{t(`${previous}.title`)}</span>
              </a>
            ) : (
              <span />
            )}
            {next ? (
              <a
                href={hrefFor(locale as Locale, `/guides/${next}`)}
                className="border-border bg-card hover:border-muted-foreground/40 rounded-[14px] border p-[18px] text-right transition-colors"
              >
                <span className="text-muted-foreground/80 block text-[11px] tracking-[0.08em] uppercase">
                  {t("next")} →
                </span>
                <span className="mt-1.5 block text-sm font-semibold">{t(`${next}.title`)}</span>
              </a>
            ) : null}
          </nav>
        </article>
      </div>
    </>
  );
}
