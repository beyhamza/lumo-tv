import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import {
  JsonLd,
  organizationJsonLd,
  softwareApplicationJsonLd,
} from "@/lib/seo/json-ld";
import { pageMetadata } from "@/lib/seo/metadata";

/**
 * The landing page (W1, docs/design/web-sprint-1.md).
 *
 * Light marketing theme, one `<h1>`, and **zero hydration**: no client
 * component, no cookie, no authenticated call — `next build` must keep listing
 * this route as prerendered, and the web workflow fails the build if it stops.
 *
 * Two things here are not decoration:
 *
 * - the hero notice and the first FAQ answer state the product rule
 *   (AGENTS.md §1): Lumo provides no content. That copy is compliance, and it
 *   is not rewritten without review;
 * - the product shot is a **hatched placeholder** in a dark chassis. A real
 *   screenshot will have to be taken on a royalty-free test set; one showing a
 *   real bouquet is the same violation as a fixture.
 *
 * <h2>No pricing</h2>
 *
 * The page used to end on two plans, Free and Plus. 0.2.0 is a free
 * application with no payment behind it (docs/backlog/dette.md §2, decision of
 * 17 September 2026), so the section, the header and footer links to it and the
 * FAQ entry about cancelling are gone rather than kept with a "coming soon":
 * a price on a page is a promise, and this build makes none. What is free
 * needs no plan card to say so; the primary call to action says it.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "Landing" });

  return pageMetadata({
    locale: locale as Locale,
    href: "/",
    title: t("metaTitle"),
    description: t("metaDescription"),
  });
}

export default async function LandingPage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("Landing");
  const brand = await getTranslations("Brand");
  const register = hrefFor(locale as Locale, "/register");

  const steps = [
    ["howStepOneTitle", "howStepOneBody"],
    ["howStepTwoTitle", "howStepTwoBody"],
    ["howStepThreeTitle", "howStepThreeBody"],
  ] as const;

  const features = [
    ["valueOneTitle", "valueOneBody"],
    ["valueTwoTitle", "valueTwoBody"],
    ["valueThreeTitle", "valueThreeBody"],
  ] as const;

  const faq = [
    ["faqOneQuestion", "faqOneAnswer"],
    ["faqTwoQuestion", "faqTwoAnswer"],
    ["faqThreeQuestion", "faqThreeAnswer"],
  ] as const;

  return (
    <>
      <JsonLd data={organizationJsonLd(brand("name"), brand("tagline"))} />
      <JsonLd
        data={softwareApplicationJsonLd({
          name: brand("name"),
          description: t("metaDescription"),
          locale: locale as Locale,
        })}
      />

      {/* ---- hero ------------------------------------------------------- */}
      <section className="mx-auto flex w-full max-w-7xl flex-col items-center gap-12 px-6 py-16 sm:px-10 lg:flex-row lg:gap-14 lg:px-16 lg:py-20">
        <div className="flex flex-1 flex-col gap-5">
          <p className="text-brand-gradient text-[13px] font-semibold tracking-[0.1em] uppercase">
            {t("heroSurtitle")}
          </p>
          <h1 className="text-4xl leading-[1.08] font-semibold tracking-[-0.025em] text-balance sm:text-5xl lg:text-[52px]">
            {t("heroTitle")}
          </h1>
          <p className="text-muted-foreground max-w-[48ch] text-lg leading-[1.55] font-light text-pretty">
            {t("heroSubtitle")}
          </p>

          <div className="mt-1 flex flex-wrap items-center gap-3.5">
            <a
              href={register}
              className="bg-primary text-primary-foreground inline-flex h-[52px] items-center rounded-full px-7 text-[15px] font-semibold"
            >
              {t("heroPrimaryCta")}
            </a>
            <a
              href="#how"
              className="border-input text-foreground inline-flex h-[52px] items-center rounded-full border px-6 text-[15px]"
            >
              {t("heroSecondaryCta")}
            </a>
          </div>

          {/* The product rule, stated where a visitor actually looks
              (AGENTS.md §1). */}
          <p className="text-muted-foreground/80 text-[13px]">{t("heroNotice")}</p>
        </div>

        {/* The product shot: a dark chassis around a hatched placeholder. The
            `.dark` scope is what lets the surfaces inside use the product
            theme's tokens rather than colours written here. */}
        <div
          aria-hidden="true"
          className="dark bg-background text-foreground flex w-full max-w-[520px] flex-none flex-col gap-3 rounded-[20px] p-[18px] lg:w-[520px]"
        >
          <div className="flex gap-1.5">
            <span className="bg-border size-2 rounded-full" />
            <span className="bg-border size-2 rounded-full" />
            <span className="bg-border size-2 rounded-full" />
          </div>
          <div className="bg-card bg-placeholder-hatch text-muted-foreground/70 flex h-[240px] items-center justify-center rounded-xl font-mono text-xs">
            {t("heroShotPlaceholder")}
          </div>
          <div className="bg-secondary h-1 rounded-full">
            <div className="bg-brand-gradient h-1 w-[58%] rounded-full" />
          </div>
        </div>
      </section>

      {/* ---- features --------------------------------------------------- */}
      <section id="features" aria-labelledby="features-title" className="border-border border-t">
        <div className="mx-auto w-full max-w-7xl px-6 py-16 sm:px-10 lg:px-16">
          <h2 id="features-title" className="text-center text-3xl font-semibold tracking-tight">
            {t("valuePropsTitle")}
          </h2>
          <div className="mt-11 grid gap-6 sm:grid-cols-3">
            {features.map(([title, body]) => (
              <article key={title} className="flex flex-col gap-3">
                <h3 className="text-lg font-semibold">{t(title)}</h3>
                <p className="text-muted-foreground text-sm leading-[1.55] font-light">{t(body)}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* ---- three steps ------------------------------------------------ */}
      <section id="how" aria-labelledby="how-title" className="border-border bg-card border-t">
        <div className="mx-auto w-full max-w-7xl px-6 py-16 sm:px-10 lg:px-16">
          <h2 id="how-title" className="text-center text-3xl font-semibold tracking-tight">
            {t("howTitle")}
          </h2>

          <ol className="mt-11 grid gap-6 sm:grid-cols-3">
            {steps.map(([title, body], index) => (
              <li
                key={title}
                className="border-border flex flex-col gap-3 rounded-[18px] border p-7"
              >
                <span
                  aria-hidden="true"
                  className="bg-secondary text-secondary-foreground flex size-9 items-center justify-center rounded-full font-semibold"
                >
                  {index + 1}
                </span>
                <h3 className="text-lg font-semibold">{t(title)}</h3>
                <p className="text-muted-foreground text-sm leading-[1.55] font-light">{t(body)}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ---- FAQ -------------------------------------------------------- */}
      <section aria-labelledby="faq-title" className="border-border bg-card border-t">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-10 px-6 py-16 sm:px-10 lg:flex-row lg:gap-16 lg:px-16">
          <div className="w-full flex-none lg:w-[320px]">
            <h2 id="faq-title" className="text-3xl font-semibold tracking-tight">
              {t("faqTitle")}
            </h2>
            <p className="text-muted-foreground mt-3 text-sm leading-[1.55] font-light">
              {t.rich("faqIntro", {
                guides: (chunks) => (
                  <a
                    href={hrefFor(locale as Locale, "/guides")}
                    className="text-secondary-foreground font-medium underline-offset-4 hover:underline"
                  >
                    {chunks}
                  </a>
                ),
              })}
            </p>
          </div>

          {/* An accordion with no JavaScript: `<details>` is the browser's own,
              and the first entry ships open. */}
          <div className="divide-border flex flex-1 flex-col divide-y">
            {faq.map(([question, answer], index) => (
              <details key={question} open={index === 0} className="group py-5">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-base font-semibold [&::-webkit-details-marker]:hidden">
                  <span>{t(question)}</span>
                  <span aria-hidden="true" className="text-muted-foreground/70">
                    <span className="group-open:hidden">+</span>
                    <span className="hidden group-open:inline">−</span>
                  </span>
                </summary>
                <p className="text-muted-foreground mt-2.5 max-w-[60ch] text-sm leading-[1.6] font-light">
                  {t(answer)}
                </p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
