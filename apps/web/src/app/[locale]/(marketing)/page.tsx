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
  const nav = await getTranslations("Nav");
  const brand = await getTranslations("Brand");

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

      <section className="mx-auto w-full max-w-5xl px-4 py-16 sm:py-24">
        <h1 className="max-w-2xl text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          {t("heroTitle")}
        </h1>
        <p className="text-muted-foreground mt-6 max-w-2xl text-lg text-pretty">
          {t("heroSubtitle")}
        </p>

        <div className="mt-8 flex flex-wrap gap-3">
          <a
            href={hrefFor(locale as Locale, "/register")}
            className="bg-primary text-primary-foreground rounded-lg px-5 py-2.5 text-sm font-medium"
          >
            {t("heroPrimaryCta")}
          </a>
          <a
            href={hrefFor(locale as Locale, "/guides")}
            className="border-border rounded-lg border px-5 py-2.5 text-sm font-medium"
          >
            {t("heroSecondaryCta")}
          </a>
        </div>

        {/* The product rule, stated where a visitor actually looks
            (AGENTS.md §1). */}
        <p className="text-muted-foreground mt-8 max-w-2xl text-sm">
          {t("heroNotice")}
        </p>
      </section>

      <section
        aria-labelledby="value-props"
        className="border-border/60 border-t"
      >
        <div className="mx-auto w-full max-w-5xl px-4 py-16">
          <h2 id="value-props" className="text-2xl font-semibold tracking-tight">
            {t("valuePropsTitle")}
          </h2>

          <div className="mt-8 grid gap-8 sm:grid-cols-3">
            {(
              [
                ["valueOneTitle", "valueOneBody"],
                ["valueTwoTitle", "valueTwoBody"],
                ["valueThreeTitle", "valueThreeBody"],
              ] as const
            ).map(([title, body]) => (
              <article key={title}>
                <h3 className="font-medium">{t(title)}</h3>
                <p className="text-muted-foreground mt-2 text-sm">{t(body)}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section aria-labelledby="how-it-works" className="border-border/60 border-t">
        <div className="mx-auto w-full max-w-5xl px-4 py-16">
          <h2 id="how-it-works" className="text-2xl font-semibold tracking-tight">
            {t("howTitle")}
          </h2>

          <ol className="mt-8 grid gap-8 sm:grid-cols-3">
            {(
              [
                ["howStepOneTitle", "howStepOneBody"],
                ["howStepTwoTitle", "howStepTwoBody"],
                ["howStepThreeTitle", "howStepThreeBody"],
              ] as const
            ).map(([title, body]) => (
              <li key={title}>
                <h3 className="font-medium">{t(title)}</h3>
                <p className="text-muted-foreground mt-2 text-sm">{t(body)}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section aria-labelledby="guides-teaser" className="border-border/60 border-t">
        <div className="mx-auto w-full max-w-5xl px-4 py-16">
          <h2 id="guides-teaser" className="text-2xl font-semibold tracking-tight">
            {t("guidesTeaserTitle")}
          </h2>
          <p className="text-muted-foreground mt-2 max-w-2xl">
            {t("guidesTeaserBody")}
          </p>
          <a
            href={hrefFor(locale as Locale, "/guides")}
            className="mt-6 inline-block text-sm font-medium underline underline-offset-4"
          >
            {nav("guides")}
          </a>
        </div>
      </section>
    </>
  );
}
