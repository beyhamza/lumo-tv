import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { MockBadge } from "@/components/site/MockBadge";
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
 * The plan quotas ("1 source, 2 devices", "unlimited") are access rights, and
 * an access right is computed server-side (`Entitlement.max_sources`, G1 in
 * docs/design/api-gaps.md). A static page cannot read an authenticated
 * endpoint, so they are written in the messages and **labelled as mock data**
 * until a public plan description exists in the contract. The prices are not:
 * the specification keeps them in i18n on purpose.
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
    ["faqFourQuestion", "faqFourAnswer"],
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

      {/* ---- pricing ---------------------------------------------------- */}
      <section id="pricing" aria-labelledby="pricing-title" className="border-border border-t">
        <div className="mx-auto w-full max-w-7xl px-6 py-16 sm:px-10 lg:px-16">
          <h2 id="pricing-title" className="text-center text-3xl font-semibold tracking-tight">
            {t("pricingTitle")}
          </h2>

          <div className="mt-11 flex flex-wrap justify-center gap-6">
            <article className="border-border bg-card flex w-full max-w-[340px] flex-col gap-4 rounded-[20px] border p-8">
              <h3 className="text-muted-foreground text-[15px] font-semibold">
                {t("pricingFreeName")}
              </h3>
              <p className="text-[40px] leading-none font-semibold">{t("pricingFreePrice")}</p>
              <ul className="text-muted-foreground flex flex-col gap-2.5 text-sm font-light">
                <li className="flex flex-wrap items-center gap-x-2">
                  <span>✓&nbsp; {t("pricingFreeQuota")}</span>
                  <MockBadge />
                </li>
                <li>✓&nbsp; {t("pricingFreeChannels")}</li>
                <li>✓&nbsp; {t("pricingFreeApps")}</li>
              </ul>
              <a
                href={register}
                className="border-input mt-auto inline-flex h-12 items-center justify-center rounded-full border text-sm font-semibold"
              >
                {t("pricingFreeCta")}
              </a>
            </article>

            <article className="dark bg-background text-foreground relative flex w-full max-w-[340px] flex-col gap-4 rounded-[20px] p-8">
              <span className="bg-brand-gradient text-primary-foreground absolute -top-3 left-8 inline-flex h-6 items-center rounded-full px-3 text-[11px] font-bold tracking-wide">
                {t("pricingPlusBadge")}
              </span>
              <h3 className="text-muted-foreground text-[15px] font-semibold">
                {t("pricingPlusName")}
              </h3>
              <p className="text-[40px] leading-none font-semibold">
                {t("pricingPlusPrice")}
                <span className="text-muted-foreground text-[15px] font-light">
                  {" "}
                  {t("pricingPlusPeriod")}
                </span>
              </p>
              <ul className="text-muted-foreground flex flex-col gap-2.5 text-sm font-light">
                <li className="text-foreground flex flex-wrap items-center gap-x-2">
                  <span>✓&nbsp; {t("pricingPlusQuota")}</span>
                  <MockBadge />
                </li>
                <li>✓&nbsp; {t("pricingPlusEpg")}</li>
                <li>✓&nbsp; {t("pricingPlusResume")}</li>
                <li>✓&nbsp; {t("pricingPlusSupport")}</li>
              </ul>
              <div className="mt-auto flex flex-col items-center gap-2">
                <a
                  href={register}
                  className="bg-primary text-primary-foreground inline-flex h-12 w-full items-center justify-center rounded-full text-sm font-semibold"
                >
                  {t("pricingPlusCta")}
                </a>
                {/* The trial and its length are billing state (G2, G3): the
                    server decides, and nothing on this page can read it. */}
                <MockBadge />
              </div>
            </article>
          </div>
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
                  {/* Cancelling goes through billing (G3), which the server does
                      not fully serve yet: the answer is the intended one, not an
                      observed one. */}
                  {answer === "faqFourAnswer" ? <MockBadge className="ml-2 align-middle" /> : null}
                </p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
