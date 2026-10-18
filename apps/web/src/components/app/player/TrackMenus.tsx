"use client";

import { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { PrefsHandle } from "@/components/app/player/use-playback-prefs";
import type { PlaybackPrefs } from "@/lib/playback/preferences";
import {
  type Track,
  type TrackLabel,
  chooseAudio,
  chooseSubtitles,
  qualityOptions,
  trackLabels,
} from "@/lib/playback/track-choice";
import type { TrackController, TrackSnapshot } from "@/lib/playback/tracks/types";

/**
 * "Audio and subtitles" and "Quality" under a web player (US-023, S13-01/02,
 * screens S13-E01 to E03).
 *
 * <h2>Honest states</h2>
 *
 * Only what the player reports is offered (`lib/playback/tracks/*`). What the
 * browser cannot tell is said with its reason; "no other track" is said only
 * where the list is actually known. A choice is shown as effective once the
 * player confirmed it; until then the menu says a change is in progress, and
 * a refusal keeps the previous selection with one Retry — no retry loop.
 *
 * <h2>Preferences</h2>
 *
 * At open, the stored languages are applied once per content (`chooseAudio`,
 * `chooseSubtitles`): absent language → the default audio track, and no
 * subtitles in another language. A manual choice wins for the current content
 * and, when the track has a language, becomes the preference on this browser;
 * a track with no language changes nothing stored. Quality is never stored.
 *
 * <h2>Focus</h2>
 *
 * Opening a menu puts focus on the effective choice, else the first usable
 * control, else Close. Escape and Close return it to the button that opened
 * the menu. Asynchronous updates never move focus.
 */
export function TrackMenus({
  controller,
  prefsHandle,
}: {
  controller: TrackController | null;
  prefsHandle: PrefsHandle;
}) {
  const t = useTranslations("Player");
  const locale = useLocale();
  const snapshot = useSyncExternalStore(
    controller?.subscribe ?? noopSubscribe,
    () => controller?.snapshot() ?? EMPTY,
    () => EMPTY,
  );
  const { prefs, update } = prefsHandle;

  const [open, setOpen] = useState<"tracks" | "quality" | null>(null);
  const [pending, setPending] = useState<"audio" | "subtitles" | "quality" | null>(null);
  const [failed, setFailed] = useState<null | { kind: "audio" | "subtitles" | "quality"; id: string | null }>(null);
  const [unsaved, setUnsaved] = useState<Partial<PlaybackPrefs> | null>(null);

  const tracksButton = useRef<HTMLButtonElement>(null);
  const qualityButton = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const panelId = useId();

  // Preferences applied once per content, when each list first becomes known.
  const applied = useRef<{ controller: TrackController | null; audio: boolean; subtitles: boolean }>({
    controller: null,
    audio: false,
    subtitles: false,
  });
  useEffect(() => {
    if (!controller || !prefs) return;
    if (applied.current.controller !== controller) {
      applied.current = { controller, audio: false, subtitles: false };
    }
    const state = applied.current;
    if (!state.audio && snapshot.audio.state === "listed") {
      state.audio = true;
      const choice = chooseAudio(snapshot.audio.tracks, prefs.audioLanguage);
      if (choice.id && choice.id !== snapshot.audio.selected) void controller.selectAudio(choice.id);
    }
    if (!state.subtitles && snapshot.subtitles.state === "listed") {
      state.subtitles = true;
      const choice = chooseSubtitles(
        snapshot.subtitles.tracks,
        prefs.subtitlesEnabled,
        prefs.subtitleLanguage,
      );
      if (choice.id !== snapshot.subtitles.selected) void controller.selectSubtitles(choice.id);
    }
  }, [controller, prefs, snapshot]);

  // Focus on opening, and only then.
  useEffect(() => {
    if (!open || !panel.current) return;
    const inputs = Array.from(panel.current.querySelectorAll<HTMLInputElement>("input[type=radio]"));
    const target =
      inputs.find((input) => input.checked && !input.disabled) ??
      inputs.find((input) => !input.disabled) ??
      panel.current.querySelector<HTMLButtonElement>("[data-close]");
    target?.focus();
  }, [open]);

  function close() {
    const trigger = open === "quality" ? qualityButton.current : tracksButton.current;
    setOpen(null);
    trigger?.focus();
  }

  function remember(change: Partial<PlaybackPrefs>) {
    setUnsaved(update(change) ? null : change);
  }

  async function select(kind: "audio" | "subtitles" | "quality", id: string | null, track?: Track) {
    if (!controller || pending) return;
    setPending(kind);
    setFailed(null);
    const ok =
      kind === "audio"
        ? await controller.selectAudio(id ?? "")
        : kind === "subtitles"
          ? await controller.selectSubtitles(id)
          : await controller.selectQuality(id ?? "auto");
    setPending(null);
    if (!ok) {
      setFailed({ kind, id });
      return;
    }
    // A track's language becomes the preference; a track without one does not.
    if (kind === "audio" && track?.language) remember({ audioLanguage: track.language });
    if (kind === "subtitles") {
      if (id === null) remember({ subtitlesEnabled: false });
      else if (track?.language) remember({ subtitlesEnabled: true, subtitleLanguage: track.language });
    }
  }

  const subtitleLanguageMissing =
    prefs?.subtitlesEnabled === true &&
    snapshot.subtitles.state === "listed" &&
    snapshot.subtitles.selected === null &&
    chooseSubtitles(snapshot.subtitles.tracks, true, prefs.subtitleLanguage).reason ===
      "language-unavailable";

  const languageName = (code: string) => {
    try {
      return new Intl.DisplayNames([locale], { type: "language" }).of(code) ?? code;
    } catch {
      return code;
    }
  };
  const label = (value: TrackLabel) =>
    [
      value.language ? capitalise(languageName(value.language)) : null,
      value.number !== null ? t("trackNumbered", { number: value.number }) : null,
      value.name,
      value.forced ? t("trackForced") : null,
    ]
      .filter(Boolean)
      .join(" · ");

  const buttonClass =
    "border-border hover:bg-secondary focus-visible:ring-ring h-9 rounded-lg border px-3 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none";

  return (
    <div className="mt-3">
      <div className="flex flex-wrap gap-2">
        <button
          ref={tracksButton}
          type="button"
          aria-expanded={open === "tracks"}
          aria-controls={panelId}
          onClick={() => setOpen(open === "tracks" ? null : "tracks")}
          className={buttonClass}
        >
          {t("tracksButton")}
        </button>
        <button
          ref={qualityButton}
          type="button"
          aria-expanded={open === "quality"}
          aria-controls={panelId}
          onClick={() => setOpen(open === "quality" ? null : "quality")}
          className={buttonClass}
        >
          {t("qualityButton")}
        </button>
      </div>

      {subtitleLanguageMissing ? (
        <p className="text-muted-foreground mt-2 text-sm">{t("subtitleLanguageMissing")}</p>
      ) : null}

      {open ? (
        <div
          id={panelId}
          ref={panel}
          role="group"
          aria-label={open === "tracks" ? t("tracksButton") : t("qualityButton")}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.preventDefault();
              close();
            }
          }}
          className="border-border bg-card mt-2 space-y-4 rounded-xl border px-4 py-3 text-sm"
        >
          {open === "tracks" ? (
            <>
              <fieldset disabled={pending !== null}>
                <legend className="font-medium">{t("audioLegend")}</legend>
                {snapshot.audio.state === "listed" ? (
                  <>
                    <TrackRadios
                      name={`${panelId}-audio`}
                      tracks={snapshot.audio.tracks}
                      selected={snapshot.audio.selected}
                      label={label}
                      unsupported={t("trackUnsupported")}
                      onSelect={(track) => void select("audio", track.id, track)}
                    />
                    {snapshot.audio.tracks.length === 1 ? (
                      <p className="text-muted-foreground mt-1">{t("audioNoOther")}</p>
                    ) : null}
                  </>
                ) : (
                  <p className="text-muted-foreground mt-1">{t(`audioUnknown.${snapshot.audio.reason}`)}</p>
                )}
              </fieldset>

              <fieldset disabled={pending !== null}>
                <legend className="font-medium">{t("subtitlesLegend")}</legend>
                {snapshot.subtitles.state === "listed" ? (
                  <>
                    <label className="mt-1 flex items-center gap-2">
                      <input
                        type="radio"
                        name={`${panelId}-subtitles`}
                        checked={snapshot.subtitles.selected === null}
                        onChange={() => void select("subtitles", null)}
                      />
                      {t("subtitlesOff")}
                    </label>
                    <TrackRadios
                      name={`${panelId}-subtitles`}
                      tracks={snapshot.subtitles.tracks}
                      selected={snapshot.subtitles.selected}
                      label={label}
                      unsupported={t("trackUnsupported")}
                      onSelect={(track) => void select("subtitles", track.id, track)}
                    />
                    {snapshot.subtitles.tracks.length === 0 ? (
                      <p className="text-muted-foreground mt-1">{t("subtitlesNone")}</p>
                    ) : null}
                    <p className="text-muted-foreground mt-1 text-xs">{t("subtitlesBurnedIn")}</p>
                  </>
                ) : (
                  <p className="text-muted-foreground mt-1">
                    {t(`subtitlesUnknown.${snapshot.subtitles.reason}`)}
                  </p>
                )}
              </fieldset>

              {/* Only where something can be chosen: a scope for no control is noise. */}
              {snapshot.audio.state === "listed" ||
              (snapshot.subtitles.state === "listed" && snapshot.subtitles.tracks.length > 0) ? (
                <p className="text-muted-foreground text-xs">{t("tracksScope")}</p>
              ) : null}
            </>
          ) : (
            <fieldset disabled={pending !== null}>
              <legend className="font-medium">{t("qualityLegend")}</legend>
              <label className="mt-1 flex items-center gap-2">
                <input
                  type="radio"
                  name={`${panelId}-quality`}
                  checked={snapshot.quality.state !== "listed" || snapshot.quality.selected === "auto"}
                  onChange={() => void select("quality", "auto")}
                />
                {t("qualityAuto")}
              </label>
              {snapshot.quality.state === "listed" ? (
                <>
                  {qualityOptions(snapshot.quality.levels).map((option) => (
                    <label key={option.id} className="mt-1 flex items-center gap-2">
                      <input
                        type="radio"
                        name={`${panelId}-quality`}
                        checked={
                          snapshot.quality.state === "listed" &&
                          snapshot.quality.selected === option.id
                        }
                        onChange={() => void select("quality", option.id)}
                      />
                      {[
                        option.height ? t("qualityHeight", { height: option.height }) : null,
                        option.kbps ? t("qualityKbps", { kbps: option.kbps }) : null,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </label>
                  ))}
                  <p className="text-muted-foreground mt-2 text-xs">{t("qualityScope")}</p>
                </>
              ) : (
                <p className="text-muted-foreground mt-1">
                  {t(`qualityAutoOnly.${snapshot.quality.reason}`)}
                </p>
              )}
            </fieldset>
          )}

          <div aria-live="polite" className="space-y-2">
            {pending ? <p>{t("switching")}</p> : null}
            {failed ? (
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-destructive">{t("switchFailed")}</p>
                <button
                  type="button"
                  onClick={() => {
                    const again = failed;
                    const tracks =
                      again.kind === "audio" && snapshot.audio.state === "listed"
                        ? snapshot.audio.tracks
                        : again.kind === "subtitles" && snapshot.subtitles.state === "listed"
                          ? snapshot.subtitles.tracks
                          : [];
                    void select(again.kind, again.id, tracks.find((track) => track.id === again.id));
                  }}
                  className="underline underline-offset-4"
                >
                  {t("retry")}
                </button>
              </div>
            ) : null}
            {unsaved ? (
              <div className="flex flex-wrap items-center gap-3">
                <p>{t("prefsNotSaved")}</p>
                <button
                  type="button"
                  onClick={() => remember(unsaved)}
                  className="underline underline-offset-4"
                >
                  {t("retrySave")}
                </button>
              </div>
            ) : null}
          </div>

          <button
            type="button"
            data-close
            onClick={close}
            className="text-muted-foreground hover:text-foreground text-sm underline underline-offset-4"
          >
            {t("close")}
          </button>
        </div>
      ) : null}
    </div>
  );
}

function TrackRadios({
  name,
  tracks,
  selected,
  label,
  unsupported,
  onSelect,
}: {
  name: string;
  tracks: Track[];
  selected: string | null;
  label: (value: TrackLabel) => string;
  unsupported: string;
  onSelect: (track: Track) => void;
}) {
  const labels = trackLabels(tracks);
  return (
    <>
      {tracks.map((track, index) => (
        <label key={track.id} className="mt-1 flex items-center gap-2">
          <input
            type="radio"
            name={name}
            checked={selected === track.id}
            disabled={!track.supported}
            onChange={() => onSelect(track)}
          />
          <span>
            {label(labels[index])}
            {!track.supported ? (
              <span className="text-muted-foreground block text-xs">{unsupported}</span>
            ) : null}
          </span>
        </label>
      ))}
    </>
  );
}

const EMPTY: TrackSnapshot = {
  audio: { state: "unknown", reason: "loading" },
  subtitles: { state: "unknown", reason: "loading" },
  quality: { state: "auto-only", reason: "loading" },
};

function noopSubscribe() {
  return () => {};
}

function capitalise(value: string) {
  return value.charAt(0).toLocaleUpperCase() + value.slice(1);
}
