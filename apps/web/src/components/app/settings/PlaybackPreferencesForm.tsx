"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePlaybackPrefs } from "@/components/app/player/use-playback-prefs";
import { PREFERENCE_LANGUAGES, type PlaybackPrefs } from "@/lib/playback/preferences";

/**
 * Settings → Playback (S13-E05, S13-02/03/04): preferred audio language,
 * subtitles on/off and their language, autoplay next — for this account on
 * this browser.
 *
 * Each change is written at once; "Saved in this browser" only when the
 * browser accepted it, and an honest refusal otherwise (Q6). The subtitle
 * language stays selectable while subtitles are off: it is kept, not erased.
 * Quality is not here — it belongs to the current playback (US-023).
 */
export function PlaybackPreferencesForm({ accountId }: { accountId: string }) {
  const t = useTranslations("Settings");
  const locale = useLocale();
  const { prefs, persistent, update } = usePlaybackPrefs(accountId, locale);
  const [status, setStatus] = useState<"saved" | "not-saved" | null>(null);

  if (!prefs) {
    return <p className="text-muted-foreground mt-3 text-sm">{t("playbackLoading")}</p>;
  }

  const change = (patch: Partial<PlaybackPrefs>) => setStatus(update(patch) ? "saved" : "not-saved");

  const names = (() => {
    try {
      return new Intl.DisplayNames([locale], { type: "language" });
    } catch {
      return null;
    }
  })();
  const nameOf = (code: string) => {
    const name = names?.of(code) ?? code;
    return name.charAt(0).toLocaleUpperCase() + name.slice(1);
  };
  // A stored language outside the list (written by a track choice) stays visible.
  const options = (current: string) =>
    (PREFERENCE_LANGUAGES as readonly string[]).includes(current)
      ? PREFERENCE_LANGUAGES
      : [current, ...PREFERENCE_LANGUAGES];

  const labelClass =
    "text-muted-foreground/80 text-[11px] font-semibold tracking-[0.1em] uppercase sm:pt-2";
  const selectClass =
    "border-border bg-background focus-visible:ring-ring h-9 rounded-lg border px-2 text-sm focus-visible:ring-2 focus-visible:outline-none";

  return (
    <div className="mt-3 space-y-3">
      <div className="bg-card grid gap-x-6 gap-y-4 rounded-2xl px-6 py-5 text-sm sm:grid-cols-[auto_1fr]">
        <label htmlFor="pref-audio" className={labelClass}>
          {t("playbackAudio")}
        </label>
        <div>
          <select
            id="pref-audio"
            value={prefs.audioLanguage}
            onChange={(event) => change({ audioLanguage: event.target.value })}
            className={selectClass}
          >
            {options(prefs.audioLanguage).map((code) => (
              <option key={code} value={code}>
                {nameOf(code)}
              </option>
            ))}
          </select>
          <p className="text-muted-foreground mt-1 max-w-[60ch]">{t("playbackAudioHint")}</p>
        </div>

        <span className={labelClass}>{t("playbackSubtitlesLabel")}</span>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={prefs.subtitlesEnabled}
            onChange={(event) => change({ subtitlesEnabled: event.target.checked })}
          />
          {t("playbackSubtitles")}
        </label>

        <label htmlFor="pref-subtitles" className={labelClass}>
          {t("playbackSubtitleLanguage")}
        </label>
        <div>
          <select
            id="pref-subtitles"
            value={prefs.subtitleLanguage}
            onChange={(event) => change({ subtitleLanguage: event.target.value })}
            className={selectClass}
          >
            {options(prefs.subtitleLanguage).map((code) => (
              <option key={code} value={code}>
                {nameOf(code)}
              </option>
            ))}
          </select>
          <p className="text-muted-foreground mt-1 max-w-[60ch]">{t("playbackSubtitleHint")}</p>
        </div>

        <span className={labelClass}>{t("playbackNextLabel")}</span>
        <div>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={prefs.autoplayNext}
              onChange={(event) => change({ autoplayNext: event.target.checked })}
            />
            {t("playbackAutoplay")}
          </label>
          <p className="text-muted-foreground mt-1 max-w-[60ch]">{t("playbackAutoplayHint")}</p>
        </div>
      </div>

      <p className="text-muted-foreground text-sm">{t("playbackQualityNote")}</p>

      <p role="status" className="text-sm">
        {status === "not-saved" || !persistent
          ? t("playbackNotSaved")
          : status === "saved"
            ? t("playbackSaved")
            : null}
      </p>
    </div>
  );
}
