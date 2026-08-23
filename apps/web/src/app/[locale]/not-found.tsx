import { getTranslations } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

/**
 * The 404 for anything under a locale prefix.
 *
 * Translated, like every other string, and it links back into the site rather
 * than leaving a dead end: a crawler that reaches this page should still find a
 * way to the content, and so should a person.
 *
 * The locale is read from the routing default rather than from params — a
 * not-found boundary receives none. next-intl resolves the active locale from
 * the request, so the text is already in the right language; only the link
 * needs a locale to build a path.
 */
export default async function LocaleNotFound() {
  const t = await getTranslations("NotFound");
  const locale = routing.defaultLocale;

  return (
    <div className="mx-auto w-full max-w-3xl flex-1 px-4 py-24">
      <h1 className="text-3xl font-semibold tracking-tight">{t("title")}</h1>
      <p className="text-muted-foreground mt-3">{t("body")}</p>
      <a
        href={hrefFor(locale, "/")}
        className="mt-8 inline-block text-sm font-medium underline underline-offset-4"
      >
        {t("backHome")}
      </a>
    </div>
  );
}
