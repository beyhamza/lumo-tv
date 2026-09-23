import { getTranslations } from "next-intl/server";
import { Wordmark } from "@/components/site/Wordmark";
import { hrefFor } from "@/i18n/navigation";
import { routing, type Locale } from "@/i18n/routing";

/**
 * The marketing header (W1, docs/design/web-sprint-1.md): logotype,
 * `Features` · `Guides`, then `Sign in` and the filled CTA. `Pricing` left
 * with the pricing section (S8-06): 0.2.0 is free, and a link named after a
 * price would lead to nothing.
 *
 * A Server Component with no interactivity at all — the language switch is two
 * links, not a dropdown, and navigation uses plain anchors rather than a
 * `Link` component. That is a deliberate trade: both would pull client
 * JavaScript onto pages whose entire purpose is to render fast from a CDN with
 * nothing blocking (docs/architecture.md §4). A content page entered from a
 * search result gains nothing from client-side routing.
 *
 * `Features` is a section of the landing page, so from a guide it is a link
 * back to `/#features` rather than an anchor that would go nowhere.
 */
export async function SiteHeader({ locale }: { locale: Locale }) {
  const t = await getTranslations("Nav");
  const brand = await getTranslations("Brand");
  const home = hrefFor(locale, "/");

  return (
    <header className="border-border border-b">
      <a
        href="#main"
        className="focus:bg-primary focus:text-primary-foreground sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-md focus:px-3 focus:py-2"
      >
        {t("skipToContent")}
      </a>

      <nav
        aria-label={brand("name")}
        className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-x-7 gap-y-2 px-6 py-5 sm:px-10 lg:px-16"
      >
        <a href={home} className="mr-2 inline-flex">
          <Wordmark />
        </a>

        <a href={`${home}#features`} className="text-muted-foreground hover:text-foreground text-sm">
          {t("features")}
        </a>
        <a
          href={hrefFor(locale, "/guides")}
          className="text-muted-foreground hover:text-foreground text-sm"
        >
          {t("guides")}
        </a>

        <div className="ml-auto flex items-center gap-4">
          <LocaleLinks current={locale} />
          <a
            href={hrefFor(locale, "/login")}
            className="text-muted-foreground hover:text-foreground text-sm"
          >
            {t("login")}
          </a>
          <a
            href={hrefFor(locale, "/register")}
            className="bg-primary text-primary-foreground inline-flex h-11 items-center rounded-full px-5 text-sm font-semibold"
          >
            {t("register")}
          </a>
        </div>
      </nav>
    </header>
  );
}

/**
 * The language switch.
 *
 * `hrefLang` on each link is what tells a crawler these are translations of one
 * another rather than unrelated pages — the in-page counterpart of the
 * `alternates` in the metadata.
 */
async function LocaleLinks({ current }: { current: Locale }) {
  const t = await getTranslations("Nav");
  const labels: Record<Locale, string> = { fr: t("french"), en: t("english") };

  return (
    <div className="flex items-center gap-2" aria-label={t("languageSwitch")}>
      {routing.locales.map((locale) => (
        <a
          key={locale}
          href={hrefFor(locale, "/")}
          hrefLang={locale}
          aria-current={locale === current ? "true" : undefined}
          className={
            locale === current
              ? "text-foreground text-sm font-medium"
              : "text-muted-foreground hover:text-foreground text-sm"
          }
        >
          {labels[locale]}
        </a>
      ))}
    </div>
  );
}
