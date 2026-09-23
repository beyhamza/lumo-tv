import { getTranslations } from "next-intl/server";
import type { Locale } from "@/i18n/routing";

/**
 * "Application" (US-025, S8-06): the interface language, read-only.
 *
 * On the web the language is the URL's — `/fr` or `/en` — which is what the
 * value shows. There is no control: choosing a language, and whether the
 * choice is the account's or this browser's, is sprint 13 (US-025, Q6). One
 * sentence says the choice is coming; nothing pretends to take it.
 */
export async function ApplicationSection({ locale }: { locale: Locale }) {
  const t = await getTranslations("Settings");
  const nav = await getTranslations("Nav");
  const languages: Record<Locale, string> = { fr: nav("french"), en: nav("english") };

  return (
    <section aria-labelledby="settings-application-title">
      <h2 id="settings-application-title" className="text-lg font-semibold tracking-tight">
        {t("sectionApplication")}
      </h2>
      <dl className="bg-card mt-3 grid gap-x-6 gap-y-3 rounded-2xl px-6 py-5 text-sm sm:grid-cols-[auto_1fr]">
        <dt className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase sm:pt-0.5">
          {t("languageLabel")}
        </dt>
        <dd>
          <span lang={locale}>{languages[locale]}</span>
          <p className="text-muted-foreground mt-1 max-w-[60ch]">{t("languageNote")}</p>
        </dd>
      </dl>
    </section>
  );
}
