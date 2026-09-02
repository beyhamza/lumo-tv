"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { saveEpisodeProgress, saveFilmProgress } from "@/actions/playback";
import { advanceMs, audibility } from "@/lib/playback/audibility";
import { savableProgress } from "@/lib/playback/progress";

/**
 * Plays one film in the browser (US-13, ADR 0007).
 *
 * <h2>This is not `ChannelPlayer`, and that is not a preference</h2>
 *
 * A film is a progressive file, not an HLS manifest. `hls.js` has nothing to do
 * with an MP4: it would be a media library loaded to hand a URL to an element
 * that could have taken it directly. So this is a bare `<video src>`, and the
 * element does the work.
 *
 * **ADR 0007's limit applies identically.** A panel served over `http://` is
 * mixed content from `https://lumo.tv`, the browser refuses it outright, and
 * nothing on this site can work around that. Same decision, same named failure
 * as `S3-11` — there is nothing new to decide here.
 *
 * <h2>Seeking, and a correction to what the task assumed</h2>
 *
 * The sprint document says moving through a film needs `Range` requests **and a
 * CORS header allowing them**. The first half is right and it is the one that
 * bites; **the second half does not hold for a media element.**
 *
 * A `<video>` without a `crossorigin` attribute issues a no-cors request. The
 * browser will range-request and play the response whatever the origin, and no
 * `Access-Control-Allow-Origin` is involved — which is exactly why the channel
 * player's native Safari path needs no CORS while its `hls.js` path does. CORS
 * governs `fetch`, and this component makes none for media.
 *
 * So the real and only condition is: **the user's server must answer `Range`
 * with `206` and advertise `Accept-Ranges`.** Many IPTV panels do not. The
 * browser then reports an empty `seekable` range, its own scrubber goes inert,
 * and without a word beside it that reads as a player that has stopped working.
 * It gets its own message rather than being folded into "unavailable", which is
 * what the task asked for and the reason it asked.
 *
 * <h2>Sound the browser cannot decode</h2>
 *
 * The picture and the sound fail separately, and only one of them is loud about
 * it. A third of a real catalogue carries **Dolby Digital** — AC-3 or E-AC-3 —
 * as its only audio track; Chromium ships no decoder for either. The H.264
 * picture then plays perfectly and the browser puts its own volume control in
 * the muted state, so what somebody sees is a film playing behind a mute button
 * that looks pressed. Pressing it does nothing, because there is nothing to
 * unmute.
 *
 * That gets a sentence, for the same reason everything else here does. The
 * reading is in `lib/playback/audibility`, along with why it takes three
 * browser-specific properties to ask a question the standard has an answer for.
 *
 * **Channels are deliberately not covered.** `ChannelPlayer` plays through
 * `hls.js` and MSE, where these properties mean something different enough that
 * carrying the rule across would be shipping untested behaviour rather than
 * sharing tested behaviour.
 *
 * <h2>Where somebody stopped (S5-11)</h2>
 *
 * Every thirty seconds while playing, on pause, and when the page goes away —
 * never per frame: `PUT /me/progress` is an idempotent upsert, not a stream.
 *
 * The page-going-away case is the one a browser makes hard. `beforeunload` is
 * unreliable on mobile and `unload` does not fire at all in some browsers, so
 * the listener is on `visibilitychange`, which is what actually fires when a tab
 * is hidden, switched away from, or closed. It saves more often than strictly
 * needed, which for an idempotent upsert costs nothing.
 *
 * <h2>Failures are named</h2>
 *
 * A black rectangle with no message is what makes someone conclude the product
 * is broken when it is their provider refusing. Every branch ends in a sentence,
 * and the ones a browser cannot get past point at the applications, which have
 * neither constraint.
 */
export function FilmPlayer({
  filmId,
  sourceId,
  name,
  resumeFromMs,
  resumeLabel,
  playbackPath = "vod",
  itemType = "VOD",
}: {
  filmId: string;
  /**
   * The source, for saving a position. **Empty switches saving off** — which is
   * no longer what an episode passes, and is kept for a page that genuinely has
   * no source to file a row under.
   */
  sourceId: string;
  name: string;
  /**
   * Where to start, **chosen on the page around this component** and never by
   * this component. Zero is the beginning, and it is somebody's answer to a
   * question they were asked — resuming is offered, not imposed.
   */
  resumeFromMs: number;
  /**
   * "Resume at 20:14", or null when there is nothing to resume.
   *
   * Formatted by the page rather than here: the position comes from the server
   * with the film, and a client component asking for it again would be a second
   * request for a number the page already has.
   */
  resumeLabel: string | null;
  /**
   * Which playback route to ask. `vod` or `episode`.
   *
   * One component for both because a browser cannot tell them apart: a
   * progressive file behind a per-playback URL, blocked identically by mixed
   * content, seekable only if the server answers `Range`, and failing with the
   * same named sentences. A second copy would be the same code with different
   * words in its comments, and the day one learnt something the other would not.
   */
  playbackPath?: "vod" | "episode";
  /**
   * Which table the saved position belongs to (S6-08).
   *
   * Beside `playbackPath` rather than derived from it, because they answer two
   * different questions — where the stream URL comes from, and what a row means —
   * and tying them together would make one of them silently follow the other the
   * day a third kind of thing is played.
   */
  itemType?: "VOD" | "EPISODE";
}) {
  const t = useTranslations("App");
  const tErrors = useTranslations("Errors");
  const videoRef = useRef<HTMLVideoElement>(null);

  const [failure, setFailure] = useState<Failure | null>(null);
  const [maxConnections, setMaxConnections] = useState<number | null>(null);
  const [seekable, setSeekable] = useState<boolean | null>(null);
  /**
   * Whether this browser is getting any sound out of the file.
   *
   * <p>State because a line depends on it, and it settles: `audibility` answers
   * the same thing on every tick once it has an answer, and React re-renders
   * nothing when a `useState` setter is handed the value it already holds.
   */
  const [sound, setSound] = useState<"audible" | "silent" | "unknown">("unknown");
  const [started, setStarted] = useState(false);
  /**
   * Where this playback was told to start.
   *
   * State rather than the prop, because "start over" changes it: the two buttons
   * are one player told two different things, not two players.
   */
  const [startAtMs, setStartAtMs] = useState(resumeFromMs);

  /**
   * The last position worth saving, kept in a ref rather than in state.
   *
   * A ref because it changes several times a second and nothing renders from it:
   * as state it would re-render the whole player on every timeupdate, over a
   * `<video>` that is drawing frames.
   */
  const position = useRef({ positionMs: 0, durationMs: null as number | null });

  /**
   * Media that has genuinely played, and where it was when last looked at.
   *
   * <p>A ref for the same reason as `position` — it moves several times a second
   * and nothing renders from it. It is *not* elapsed clock time: a stalled panel
   * would run the clock without a frame of sound ever having been possible, and
   * declare silence over a file nobody has heard yet.
   */
  const played = useRef({ totalMs: 0, atMs: 0 });

  const save = useCallback(() => {
    // Nothing to file a row under. Nothing is written rather than something
    // written against a key the server cannot make sense of.
    if (!sourceId) return;

    const { positionMs, durationMs } = position.current;
    // `isLive` is false and stated rather than assumed: this component only ever
    // plays a file, and the guard is what keeps that true if it is ever reused.
    if (!savableProgress({ positionMs, isLive: false })) return;

    // Two actions rather than one with a type argument, for the reason written on
    // them: the two ids come from two tables.
    if (itemType === "EPISODE") {
      void saveEpisodeProgress({ sourceId, episodeId: filmId, positionMs, durationMs });
    } else {
      void saveFilmProgress({ sourceId, filmId, positionMs, durationMs });
    }
  }, [filmId, sourceId, itemType]);

  useEffect(() => {
    if (!started) return;
    let disposed = false;

    async function start() {
      setFailure(null);
      // A different file, or the same one from the beginning: nothing learnt
      // about the last playback applies, and a stale "no sound" line under a
      // film that has sound is the exact mistake this feature is meant to avoid.
      setSound("unknown");
      played.current = { totalMs: 0, atMs: 0 };

      let payload: { url: string; maxConnections: number | null };
      try {
        // Fetched at the moment of playing and never rendered into the page: the
        // URL carries the user's panel credentials.
        const response = await fetch(`/api/playback/${playbackPath}/${filmId}`, {
          cache: "no-store",
        });
        const body = await response.json();
        if (!response.ok) {
          setFailure({ kind: "api", code: String(body.code ?? "INTERNAL_ERROR") });
          return;
        }
        payload = body;
      } catch {
        setFailure({ kind: "api", code: "NETWORK" });
        return;
      }

      if (disposed) return;
      setMaxConnections(payload.maxConnections);

      // Checked before anything is attempted, because the browser will not
      // report this one usefully: it refuses the request outright and the
      // console message never reaches the user.
      if (window.location.protocol === "https:" && payload.url.startsWith("http:")) {
        setFailure({ kind: "mixed-content" });
        return;
      }

      const video = videoRef.current;
      if (!video) return;

      // No `crossorigin` attribute, deliberately. Setting one would turn this
      // into a CORS request and break every panel that does not send the header
      // — for a capability (canvas, Web Audio) nothing here uses.
      video.src = payload.url;
      // Set before play, so the browser opens the file at the offset rather than
      // downloading its way there. On a server that ignores `Range` it silently
      // does nothing, which is the honest outcome — the same server cannot seek
      // either.
      if (startAtMs > 0) video.currentTime = startAtMs / 1000;
      void video.play().catch(() => {
        // Autoplay refused by policy is not a playback failure, and saying so
        // would be wrong: the file loaded, the controls work, the visitor
        // presses play. Only a real error reaches `onError` below.
      });
    }

    void start();
    return () => {
      disposed = true;
    };
  }, [filmId, started, startAtMs, playbackPath]);

  // The thirty-second loop, plus the two moments that matter more than any tick:
  // the tab going away, and the component being taken down.
  useEffect(() => {
    if (!started) return;

    const timer = setInterval(save, SAVE_EVERY_MS);
    const onHidden = () => {
      if (document.visibilityState === "hidden") save();
    };
    document.addEventListener("visibilitychange", onHidden);

    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onHidden);
      // Last, and it is the one that catches navigating away from the page.
      save();
    };
  }, [started, save]);

  return (
    <section className="mt-6">
      {!started ? (
        <div className="flex flex-wrap gap-3">
          {/* Resume first, start-over beside it, both visible. Neither is pressed
              on anybody's behalf — see the page's own documentation. */}
          {resumeLabel ? (
            <button
              type="button"
              onClick={() => {
                setStartAtMs(resumeFromMs);
                setStarted(true);
              }}
              className="bg-primary text-primary-foreground h-10 rounded-lg px-5 text-sm font-medium"
            >
              {resumeLabel}
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => {
              setStartAtMs(0);
              setStarted(true);
            }}
            className={
              resumeLabel
                ? "bg-secondary text-secondary-foreground h-10 rounded-lg px-5 text-sm font-medium"
                : "bg-primary text-primary-foreground h-10 rounded-lg px-5 text-sm font-medium"
            }
          >
            {t(resumeLabel ? "filmsStartOver" : "filmsPlay")}
          </button>
        </div>
      ) : null}

      <video
        ref={videoRef}
        controls
        playsInline
        preload="none"
        aria-label={name}
        // Once metadata is in, the browser knows whether the server answered a
        // range request. An empty `seekable` list is a server that sent the
        // whole body for every offset — the condition this player has to name.
        onLoadedMetadata={(event) => {
          const video = event.currentTarget;
          setSeekable(video.seekable.length > 0 && Number.isFinite(video.duration));
        }}
        onTimeUpdate={(event) => {
          const video = event.currentTarget;
          const positionMs = video.currentTime * 1000;
          position.current = {
            positionMs,
            durationMs: Number.isFinite(video.duration) ? video.duration * 1000 : null,
          };

          // Asked here rather than on `loadedmetadata` because the reading
          // Chromium offers is a count of decoded bytes, which says nothing
          // until decoding has run. The three properties are read through an
          // unknown-keyed view: two of them exist in one engine each, and typing
          // them onto `HTMLVideoElement` would be claiming a standard that is
          // precisely what is missing here.
          const readings = video as unknown as {
            audioTracks?: { length: number };
            mozHasAudio?: boolean;
            webkitAudioDecodedByteCount?: number;
          };
          played.current = {
            totalMs: played.current.totalMs + advanceMs(played.current.atMs, positionMs),
            atMs: positionMs,
          };
          setSound(
            audibility({
              audioTrackCount: readings.audioTracks?.length,
              hasAudio: readings.mozHasAudio,
              audioBytesDecoded: readings.webkitAudioDecodedByteCount,
              playedMs: played.current.totalMs,
              muted: video.muted,
            }),
          );
        }}
        // A pause is the single most likely moment for somebody to walk away, so
        // it is worth a write of its own rather than waiting up to thirty
        // seconds for the next tick.
        onPause={save}
        onError={() =>
          setFailure((current) =>
            // Not overwritten: a named failure set above is more specific than
            // anything the element can report.
            current ?? { kind: "unplayable" },
          )
        }
        className={started ? "mt-3 aspect-video w-full rounded-lg bg-black" : "hidden"}
      />

      {maxConnections ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("playerMaxConnections", { count: maxConnections })}
        </p>
      ) : null}

      {/* Only when it is false. "Seeking works" is what a scrubber already says,
          and a line confirming it would be noise on every film that behaves. */}
      {seekable === false && !failure ? (
        <p className="text-muted-foreground mt-2 text-sm">{t("filmsNoSeeking")}</p>
      ) : null}

      {/* Only on "silent". "unknown" is a browser that would not say, and a
          hedged line under a film whose sound is fine is worse than nothing —
          it sends somebody to install an application over a problem they do not
          have. The apps line follows, as it does for mixed content, because
          here too it is the answer rather than a consolation. */}
      {sound === "silent" && !failure ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("playerNoAudio")} {t("playerUseApps")}
        </p>
      ) : null}

      {failure ? (
        <div role="alert" className="border-destructive/40 mt-3 rounded-lg border px-4 py-3">
          <p className="font-medium">{t("playerFailedTitle")}</p>
          <p className="mt-1 text-sm">
            {failure.kind === "api"
              ? messageForCode(failure.code, tErrors, t)
              : t(bodyKey(failure.kind))}
          </p>
          {/* Offered only where it is true. A browser cannot get past mixed
              content, and telling someone to try again would be telling them to
              do the same thing twice. */}
          {failure.kind === "mixed-content" ? (
            <p className="text-muted-foreground mt-2 text-sm">{t("playerUseApps")}</p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

/** See the class documentation: an upsert, not a stream. */
const SAVE_EVERY_MS = 30_000;

type Failure =
  | { kind: "api"; code: string }
  | { kind: "mixed-content" }
  | { kind: "unplayable" };

function bodyKey(kind: Exclude<Failure["kind"], "api">) {
  return kind === "mixed-content" ? "playerMixedContent" : "playerUnplayable";
}

/**
 * An API refusal, said in the API's own words where we have them.
 *
 * `VOD_ITEM_NOT_FOUND` joins the three the channel player already knows; the
 * rest fall back rather than crashing, because the contract allows new codes
 * within v1.
 */
function messageForCode(
  code: string,
  tErrors: (key: string) => string,
  t: (key: string) => string,
): string {
  const known = [
    "SOURCE_NOT_READY",
    "SOURCE_EXPIRED",
    "SOURCE_MAX_CONNECTIONS",
    "VOD_ITEM_NOT_FOUND",
    "EPISODE_NOT_FOUND",
    "UNAUTHENTICATED",
  ];
  return known.includes(code) ? tErrors(code) : t("playerUnplayable");
}
