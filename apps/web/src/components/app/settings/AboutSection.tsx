import { getTranslations } from "next-intl/server";
import { hrefFor } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { appVersion } from "@/lib/env.public";

/**
 * "Help and information" (US-025, S8-06): the deployed version and the
 * guides.
 *
 * <h2>The version is the build's, never a constant</h2>
 *
 * `appVersion()` is stamped at build time from `package.json`
 * (`next.config.ts`), or from `NEXT_PUBLIC_APP_VERSION` when a deployment sets
 * it. A version written here by hand would be the roadmap's, and the design
 * asks for the one actually installed (docs/design/0.2.0/settings.md). When
 * the build carries none, the row is absent rather than showing "unknown".
 *
 * <h2>Privacy and terms are not linked, because they do not exist</h2>
 *
 * The marketing footer shows both as unlinked words for the same reason. The
 * day the pages exist, the links go here; until then a link to a 404 is not
 * "information".
 */
export async function AboutSection({ locale }: { locale: Locale }) {
  const t = await getTranslations("Settings");
  const version = appVersion();

  return (
    <section aria-labelledby="settings-about-title">
      <h2 id="settings-about-title" className="text-lg font-semibold tracking-tight">
        {t("sectionAbout")}
      </h2>
      <dl className="bg-card mt-3 grid gap-x-6 gap-y-3 rounded-2xl px-6 py-5 text-sm sm:grid-cols-[auto_1fr]">
        {version ? (
          <>
            <dt className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase sm:pt-0.5">
              {t("versionLabel")}
            </dt>
            <dd className="font-mono">{version}</dd>
          </>
        ) : null}
        <dt className="text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase sm:pt-0.5">
          {t("guidesLabel")}
        </dt>
        <dd>
          <a
            href={hrefFor(locale, "/guides")}
            className="text-foreground underline underline-offset-4"
          >
            {t("guidesLink")}
          </a>
          <p className="text-muted-foreground mt-1 max-w-[60ch]">{t("guidesBody")}</p>
        </dd>
      </dl>
    </section>
  );
}
