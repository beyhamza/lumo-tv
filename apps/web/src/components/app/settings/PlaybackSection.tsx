import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations } from "next-intl/server";
import { PlaybackPreferencesForm } from "@/components/app/settings/PlaybackPreferencesForm";

/**
 * "Playback" (US-025, S13-E05, S13-04): the preferences the web player
 * applies — audio language, subtitles and their language, next episode
 * automatically (US-023, S13-02/03).
 *
 * They live in this browser (`lib/playback/preferences.ts`), so the form is
 * the one client island of Settings, under a provider carrying the Settings
 * messages only. The section heading and scope sentence are rendered here,
 * on the server, so the page says what the section is before any script runs.
 */
export async function PlaybackSection({ accountId }: { accountId: string }) {
  const t = await getTranslations("Settings");
  const messages = await getMessages();

  return (
    <section aria-labelledby="settings-playback-title">
      <h2 id="settings-playback-title" className="text-lg font-semibold tracking-tight">
        {t("sectionPlayback")}
      </h2>
      <p className="text-muted-foreground/80 mt-1 text-[13px]">{t("playbackScope")}</p>
      <noscript>
        <p className="text-muted-foreground mt-3 text-sm">{t("playbackNeedsScript")}</p>
      </noscript>
      <NextIntlClientProvider messages={{ Settings: messages.Settings }}>
        <PlaybackPreferencesForm accountId={accountId} />
      </NextIntlClientProvider>
    </section>
  );
}
