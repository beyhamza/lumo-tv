"use client";

import { useTranslations } from "next-intl";
import type { CastView } from "@/components/app/player/use-cast";
import { asClock } from "@/lib/playback/progress";

const buttonClass =
  "border-border hover:bg-secondary focus-visible:ring-ring h-9 rounded-lg border px-3 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none disabled:opacity-50";

/**
 * The Cast button, and the remote control once the receiver plays (US-027,
 * S16-02 to S16-05).
 *
 * Nothing is drawn while there is no device: a button that opens an empty
 * picker is a button that does not work. While casting, the page becomes a
 * remote — play/pause, back and forward when the receiver can seek, volume,
 * stop. A refusal is named in one sentence and playback carries on here; a
 * stop offers to resume here, at the position the receiver reached.
 */
export function CastPanel({
  view,
  refused,
  seekable,
  resumeAtMs,
  onCast,
  onPlayPause,
  onSeekBy,
  onVolume,
  onStop,
  onResumeHere,
}: {
  view: CastView;
  refused: boolean;
  /** False for a channel, and for a file whose server ignores `Range` (decision 7). */
  seekable: boolean;
  /** Where casting stopped, when resuming here is on offer. */
  resumeAtMs: number | null;
  onCast(): void;
  onPlayPause(): void;
  onSeekBy(seconds: number): void;
  onVolume(level: number): void;
  onStop(): void;
  onResumeHere?(): void;
}) {
  const t = useTranslations("Player");

  return (
    <>
      {view.phase === "available" || view.phase === "connecting" ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={onCast}
            disabled={view.phase === "connecting"}
            className={buttonClass}
          >
            {view.phase === "connecting"
              ? view.device
                ? t("castConnectingTo", { device: view.device })
                : t("castConnecting")
              : t("castButton")}
          </button>
        </div>
      ) : null}

      {view.phase === "casting" ? (
        <section
          aria-label={t("castRemoteLabel", { device: view.device })}
          className="border-border mt-3 rounded-xl border px-4 py-3"
        >
          <p className="font-medium">{t("castOn", { device: view.device })}</p>
          <p className="text-muted-foreground mt-1 text-sm" aria-live="polite">
            {asClock(view.positionMs)}
            {view.durationMs ? ` / ${asClock(view.durationMs)}` : null}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {seekable && view.canSeek ? (
              <button type="button" onClick={() => onSeekBy(-10)} className={buttonClass}>
                {t("castBack")}
              </button>
            ) : null}
            <button type="button" onClick={onPlayPause} className={buttonClass}>
              {view.paused ? t("castPlay") : t("castPause")}
            </button>
            {seekable && view.canSeek ? (
              <button type="button" onClick={() => onSeekBy(10)} className={buttonClass}>
                {t("castForward")}
              </button>
            ) : null}
            <label className="flex items-center gap-2 text-sm">
              {t("castVolume")}
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={view.volume}
                onChange={(event) => onVolume(Number(event.currentTarget.value))}
              />
            </label>
            <button type="button" onClick={onStop} className={buttonClass}>
              {t("castStop")}
            </button>
          </div>
        </section>
      ) : null}

      {refused ? (
        <p role="alert" className="border-destructive/40 mt-3 rounded-lg border px-4 py-3 text-sm">
          {t("castRefused")}
        </p>
      ) : null}

      {resumeAtMs !== null && onResumeHere && view.phase !== "casting" ? (
        <div role="status" className="mt-3 flex flex-wrap items-center gap-3 text-sm">
          <span>{t("castStopped")}</span>
          <button type="button" onClick={onResumeHere} className={buttonClass}>
            {t("castResumeHere", { at: asClock(resumeAtMs) })}
          </button>
        </div>
      ) : null}
    </>
  );
}
