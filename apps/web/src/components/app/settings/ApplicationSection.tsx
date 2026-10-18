import { getTranslations } from "next-intl/server";
import { chooseInterfaceLanguage } from "@/actions/interface-language";
import { Button } from "@/components/ui/button";
import { routing, type Locale } from "@/i18n/routing";

/**
 * "Application" (US-025, S13-E08, S13-04): the interface language, French or
 * English, for this browser.
 *
 * The current language is the URL's. The other one is a button in a plain
 * form posting to `chooseInterfaceLanguage`, which remembers the choice for
 * this browser and reopens this section in that language — no JavaScript, no
 * write to the account (Q6). Each language is named in itself (`lang`), so
 * somebody who cannot read the current interface still finds theirs.
 *
 * Distinct from the playback languages, which live in Playback and do not
 * follow this choice (PS-01).
 */
export async function ApplicationSection({ locale }: { locale: Locale }) {
  const t = await getTranslations("Settings");
  // Each language in its own words: "Français", "English".
  const names: Record<Locale, string> = {
    fr: (await getTranslations({ locale: "fr", namespace: "Nav" }))("french"),
    en: (await getTranslations({ locale: "en", namespace: "Nav" }))("english"),
  };
  const others = routing.locales.filter((candidate) => candidate !== locale);

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
          <p>
            <span lang={locale} className="font-medium">
              {names[locale]}
            </span>{" "}
            <span className="text-muted-foreground">— {t("languageCurrent")}</span>
          </p>
          <form action={chooseInterfaceLanguage} className="mt-3 flex flex-wrap gap-2">
            {others.map((other) => (
              <Button key={other} type="submit" name="locale" value={other} variant="secondary">
                <span lang={other}>{names[other]}</span>
              </Button>
            ))}
          </form>
          <p className="text-muted-foreground mt-2 max-w-[60ch]">{t("languageNote")}</p>
        </dd>
      </dl>
    </section>
  );
}
