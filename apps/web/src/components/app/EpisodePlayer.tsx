"use client";

import { useEffect, useReducer, useRef } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { FilmPlayer } from "@/components/app/FilmPlayer";
import { usePlaybackPrefs } from "@/components/app/player/use-playback-prefs";
import type { LaunchKind } from "@/lib/playback/continue-launch";
import { PLAYING, endReducer } from "@/lib/playback/next-episode";
import { asClock } from "@/lib/playback/progress";
import type { PlayerActions } from "@/lib/playback/sheet-action";

/**
 * Plays one episode in the browser (US-15, ADR 0007).
 *
 * <h2>The film player, and that is not laziness</h2>
 *
 * An episode and a film are the same thing to a browser: a progressive file
 * behind a per-playback URL. Mixed content blocks both identically, seeking
 * depends on `Range` for both identically, and the named failures are the same
 * sentences. A second component would be the same code with a different word in
 * its comments, and the day one of them learnt something the other would not.
 *
 * <h2>Where somebody stopped (S6-08)</h2>
 *
 * The film player's own saving, pointed at the other table. An episode's position
 * is written under `EPISODE` and read back by a rail of **series** — progress is
 * recorded on an episode, resuming is thought about in series, and the turn between
 * the two needs the tree.
 *
 * <h2>What follows the real end (S13-03)</h2>
 *
 * Which episode comes next, and where it starts, is decided by the page from
 * the tree and the saved positions (`nextAfter`, S12): an episode already
 * started resumes at its position (CW-12), one finished starts over (CW-21).
 * This component only decides **when**, through `endReducer`:
 *
 * - autoplay on (Settings → Playback, this browser): a 5-second countdown,
 *   cancelled by any real input on the page — pointer, key, wheel, touch.
 *   Focusing "Play now" programmatically is not input and cancels nothing;
 * - autoplay off: the card and "Play next episode", no countdown (CW-14);
 * - no next episode: back to the series sheet, no empty countdown (CW-15).
 *
 * Only `ended` starts any of it. Passing 95 % plays on (CW-08).
 *
 * The next episode opens as a card's launch would (`launch=next&at=…`), with a
 * client-side navigation: the same document keeps the gesture that started
 * this episode, which is what lets the browser start the next one with sound.
 */
export function EpisodePlayer({
  episodeId,
  sourceId,
  name,
  actions,
  audioCodec,
  sourceHref,
  continueHref,
  launch,
  accountId,
  next,
  seriesHref,
  posterUrl = null,
}: {
  episodeId: string;
  sourceId: string;
  name: string;
  /**
   * The audio codec the panel named for this episode, straight from the tree.
   *
   * <p>An episode has this and a film does not, and the difference is where the
   * value comes from rather than a decision made here: one `get_series_info`
   * describes every episode of a series, while a film's detail is a call per
   * film that the ingestion deliberately does not make.
   */
  audioCodec: string | null;
  /**
   * The buttons that start this episode and where each starts, **chosen on the
   * page around this component** (S12-03): Watch, Resume at… with Start over,
   * or Watch again.
   */
  actions: PlayerActions;
  /** The source's own page, for a refusal that sends the user there. */
  sourceHref: string;
  /** Where "Continue" goes once the source is gone: the home page. */
  continueHref: string;
  /** Opened from a Continue card: start at once (S12-05). See `FilmPlayer`. */
  launch?: { kind: LaunchKind; fromMs: number; seriesHref?: string } | null;
  /** Whose preferences apply on this browser (S13-02). */
  accountId: string;
  /** The episode after this one, decided by the page; null after the last one. */
  next: { href: string; season: number; episode: number; title: string | null; startMs: number } | null;
  /** The series sheet without a playing episode: where the last episode returns (CW-15). */
  seriesHref: string;
  /** The series' poster, shown by the Chromecast while casting (US-027). */
  posterUrl?: string | null;
}) {
  const t = useTranslations("Player");
  const router = useRouter();
  const { prefs } = usePlaybackPrefs(accountId, useLocale());
  const [end, dispatch] = useReducer(endReducer, PLAYING);
  const playNow = useRef<HTMLButtonElement>(null);

  // The countdown, and what cancels it: real input anywhere on the page.
  useEffect(() => {
    if (end.phase !== "counting") return;
    const timer = setInterval(() => dispatch({ type: "tick" }), 1000);
    const interact = () => dispatch({ type: "interact" });
    const kinds = ["pointerdown", "keydown", "wheel", "touchstart"] as const;
    for (const kind of kinds) document.addEventListener(kind, interact, { capture: true });
    return () => {
      clearInterval(timer);
      for (const kind of kinds) document.removeEventListener(kind, interact, { capture: true });
    };
  }, [end.phase]);

  // The card takes focus when it appears — which is not an interaction.
  const offered = end.phase === "counting" || end.phase === "offered";
  useEffect(() => {
    if (offered) playNow.current?.focus();
  }, [offered]);

  useEffect(() => {
    if (end.phase === "launch-next" && next) router.push(next.href);
    if (end.phase === "to-series") router.push(seriesHref);
  }, [end.phase, next, seriesHref, router]);

  return (
    <>
      <FilmPlayer
        filmId={episodeId}
        sourceId={sourceId}
        name={name}
        actions={actions}
        playbackPath="episode"
        itemType="EPISODE"
        audioCodec={audioCodec}
        sourceHref={sourceHref}
        continueHref={continueHref}
        launch={launch}
        accountId={accountId}
        posterUrl={posterUrl}
        onEndedChange={(ended) => {
          if (!ended) {
            dispatch({ type: "resumed" });
            return;
          }
          // The card would sit behind a fullscreen video, out of sight.
          if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
          dispatch({
            type: "ended",
            hasNext: next !== null,
            // Q6 default until the preferences are read: on.
            autoplay: prefs?.autoplayNext ?? true,
          });
        }}
      />

      {offered && next ? (
        <section
          aria-labelledby="next-episode-title"
          className="border-border mt-3 rounded-xl border px-4 py-3"
        >
          <h2 id="next-episode-title" className="font-medium">
            {t("nextTitle", { season: next.season, episode: next.episode })}
            {next.title ? ` · ${next.title}` : null}
          </h2>
          {next.startMs > 0 ? (
            <p className="text-muted-foreground mt-1 text-sm">
              {t("nextResumeAt", { at: asClock(next.startMs) })}
            </p>
          ) : null}
          {end.phase === "counting" ? (
            <p role="status" className="mt-1 text-sm">
              {t("nextCountdown", { seconds: end.remaining })}
            </p>
          ) : end.cancelled ? (
            <p role="status" className="text-muted-foreground mt-1 text-sm">
              {t("nextCancelled")}
            </p>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-3">
            <button
              ref={playNow}
              type="button"
              onClick={() => dispatch({ type: "play-now" })}
              className="bg-primary text-primary-foreground focus-visible:ring-ring h-10 rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
            >
              {end.phase === "counting" ? t("nextPlayNow") : t("nextPlay")}
            </button>
            <a
              href={seriesHref}
              className="bg-secondary text-secondary-foreground focus-visible:ring-ring inline-flex h-10 items-center rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
            >
              {t("backToSeries")}
            </a>
          </div>
        </section>
      ) : null}
    </>
  );
}
