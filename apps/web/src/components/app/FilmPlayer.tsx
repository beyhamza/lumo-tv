"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { saveEpisodeProgress, saveFilmProgress } from "@/actions/playback";
import { PlaybackRefusalMessage } from "@/components/app/PlaybackRefusalMessage";
import { SourceDeleted } from "@/components/app/SourceDeleted";
import { advanceMs, audibility, browserDecodes } from "@/lib/playback/audibility";
import { savableProgress } from "@/lib/playback/progress";
import type { PlayerActions } from "@/lib/playback/sheet-action";
import { useSourceGone } from "@/lib/playback/use-source-gone";
import { WatchMeter, isActuallyPlaying } from "@/lib/playback/watch-meter";

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
 * That gets a sentence, for the same reason everything else here does, and it
 * gets it **twice over**: the panel names the codec in the sheet an episode tree
 * comes from, so the contract carries it and the line can be read before
 * anything is fetched; and the element is watched while it plays, which is the
 * only answer available for the many sources that declare nothing. The reading
 * is in `lib/playback/audibility`, along with why it takes three
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
 * Since S12-02 each save also carries the time **actually watched** since the
 * previous one — wall-clock intervals measured by `WatchMeter` while the element
 * really plays, a seek, a pause or a stall producing none — with when the
 * position was read and when this page session first really played. Whether
 * that adds up to a card in Continue is decided by the server (`adr/0012`).
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
 *
 * <h2>The source deleted while this plays</h2>
 *
 * Asked once a minute from the moment playback is requested (`useSourceGone`). On
 * a deletion **the server confirmed**, the file is let go of, the player is
 * replaced by one sentence and one "Continue", and nothing else is started
 * (US-024). The saving loop stops with it; its parting write is refused by the
 * server, the progress rows having gone with the source by cascade. An outage is
 * not a deletion and changes nothing here.
 */
export function FilmPlayer({
  filmId,
  sourceId,
  name,
  actions,
  playbackPath = "vod",
  itemType = "VOD",
  audioCodec = null,
  sourceHref,
  continueHref,
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
   * The buttons that start playback and where each starts, **chosen on the page
   * around this component** and never by it (S12-03): Watch, Resume at 20:14
   * with Start over beside it, or Watch again. Resuming is offered, not imposed.
   *
   * Decided and worded by the page (`lib/playback/sheet-action.ts`): the position
   * comes from the server with the film, and a client component asking for it
   * again would be a second request for a number the page already has.
   */
  actions: PlayerActions;
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
   * The audio codec the panel named, from the contract — `ac3`, `aac`, or null.
   *
   * <p>The whole point of it being here is **when** it is available: before a
   * byte is fetched, so somebody is told before they press play rather than
   * three seconds into a silent film. What the element later reports still has
   * the last word for anything this does not know, which is most sources.
   */
  audioCodec?: string | null;
  /**
   * Which table the saved position belongs to (S6-08).
   *
   * Beside `playbackPath` rather than derived from it, because they answer two
   * different questions — where the stream URL comes from, and what a row means —
   * and tying them together would make one of them silently follow the other the
   * day a third kind of thing is played.
   */
  itemType?: "VOD" | "EPISODE";
  /**
   * The source's own page, where a refusal that needs the source fixed — or a
   * refresh followed — sends the user. Built by the page, locale included.
   */
  sourceHref: string;
  /** Where "Continue" goes once the source is gone: the home page. */
  continueHref: string;
}) {
  const t = useTranslations("App");
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
  const [startAtMs, setStartAtMs] = useState(actions.primary.fromMs);
  // Only once playback has been asked for: a film page that is merely open is a
  // page, and pages find out about a deletion by being navigated.
  const gone = useSourceGone(sourceId, started);

  /**
   * The last position worth saving, kept in a ref rather than in state.
   *
   * A ref because it changes several times a second and nothing renders from it:
   * as state it would re-render the whole player on every timeupdate, over a
   * `<video>` that is drawing frames.
   */
  const position = useRef({
    positionMs: 0,
    durationMs: null as number | null,
    // When the position was read, for the server to tell the most recent
    // playback across devices (`observed_at`, CW-30/32).
    observedAt: null as string | null,
  });

  /**
   * Time actually watched (S12-02): wall-clock intervals while the element is
   * genuinely playing, sent with each save. A ref, created once per mount: the
   * page session is the meter's lifetime, and its first real playback is the
   * `session_started_at` the server compares with a hide (CW-18, CW-31).
   */
  const [watchMeter] = useState(() => new WatchMeter());

  /**
   * Opens or closes the current interval from what the element reports. Called
   * on every media event that can change the answer, so no single event has to
   * be trusted to arrive — a seek within the buffer, for instance, fires no
   * `playing` when it ends.
   */
  const syncMeter = useCallback(() => {
    const video = videoRef.current;
    const now = Date.now();
    if (
      video &&
      isActuallyPlaying({
        paused: video.paused,
        seeking: video.seeking,
        ended: video.ended,
        readyState: video.readyState,
        visible: document.visibilityState === "visible",
      })
    ) {
      watchMeter.start(now);
    } else {
      watchMeter.stop(now);
    }
  }, [watchMeter]);

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

    const { positionMs, durationMs, observedAt } = position.current;
    // `isLive` is false and stated rather than assumed: this component only ever
    // plays a file, and the guard is what keeps that true if it is ever reused.
    if (!savableProgress({ positionMs, isLive: false })) return;

    // Every save carries what was watched since the previous one, the open
    // interval cut here and left running. Saving starts with the first frame:
    // whether 30 seconds are reached is the server's call, not this player's.
    const watchedSegments = watchMeter.take(Date.now());
    const extras = {
      watchedSegments,
      observedAt: observedAt ?? undefined,
      sessionStartedAt: watchMeter.sessionStartedAt,
    };

    // Two actions rather than one with a type argument, for the reason written on
    // them: the two ids come from two tables.
    const sent =
      itemType === "EPISODE"
        ? saveEpisodeProgress({ sourceId, episodeId: filmId, positionMs, durationMs, ...extras })
        : saveFilmProgress({ sourceId, filmId, positionMs, durationMs, ...extras });

    // An interval that did not reach the server goes with the next save. Sent
    // twice it adds nothing; lost, it would be watched time that never counted.
    void sent
      .then((outcome) => {
        if (outcome === "retry") watchMeter.giveBack(watchedSegments);
      })
      .catch(() => watchMeter.giveBack(watchedSegments));
  }, [filmId, sourceId, itemType, watchMeter]);

  useEffect(() => {
    if (!started || gone) return;
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
  }, [filmId, started, startAtMs, playbackPath, gone]);

  // The thirty-second loop, plus the two moments that matter more than any tick:
  // the tab going away, and the component being taken down.
  useEffect(() => {
    // `gone` ends the loop for good. What its cleanup does on the way out is
    // written on the cleanup.
    if (!started || gone) return;

    const timer = setInterval(save, SAVE_EVERY_MS);
    // A hidden page closes the interval before the save that follows; a page
    // shown again reopens it only if the element is still playing.
    const onHidden = () => {
      syncMeter();
      if (document.visibilityState === "hidden") save();
    };
    document.addEventListener("visibilitychange", onHidden);

    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onHidden);
      // Last, and it is the one that catches navigating away from the page.
      //
      // It also runs when `gone` flips to true, and that write is then refused
      // with `404 SOURCE_NOT_FOUND` (contract, `PUT /me/progress`): the rows
      // were removed with the source. One refused request, and nothing to keep —
      // cheaper than threading a second flag through a cleanup that cannot see
      // the next value of `gone`.
      watchMeter.stop(Date.now());
      save();
    };
  }, [started, save, gone, syncMeter, watchMeter]);

  if (gone) {
    // The `<video>` is unmounted with everything else, which is what lets go of
    // the file: a media element removed from the document stops loading.
    return <SourceDeleted continueHref={continueHref} />;
  }

  return (
    <section className="mt-6">
      {!started ? (
        <div className="flex flex-wrap gap-3">
          {/* Primary first — Watch, Resume at…, or Watch again — and Start over
              beside a resume, both visible. Neither is pressed on anybody's
              behalf — see the page's own documentation. */}
          {[actions.primary, actions.secondary].map((action, index) =>
            action ? (
              <button
                key={index}
                type="button"
                onClick={() => {
                  setStartAtMs(action.fromMs);
                  setStarted(true);
                }}
                className={
                  index === 0
                    ? "bg-primary text-primary-foreground focus-visible:ring-ring h-10 rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
                    : "bg-secondary text-secondary-foreground focus-visible:ring-ring h-10 rounded-lg px-5 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
                }
              >
                {action.label}
              </button>
            ) : null,
          )}
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
            observedAt: new Date().toISOString(),
          };
          // A safety net for the meter: some transitions back to playing fire
          // no event of their own. Cheap — an idempotent check.
          syncMeter();

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
        // seconds for the next tick. The interval closes first, so the save
        // carries it.
        onPause={() => {
          syncMeter();
          save();
        }}
        // Watched time (S12-02): every event that can start or stop real
        // playback re-reads the element. Seeking, waiting and ending close the
        // interval; playing and a finished seek reopen it if it really plays.
        onPlaying={syncMeter}
        onWaiting={syncMeter}
        onSeeking={syncMeter}
        onSeeked={syncMeter}
        onStalled={syncMeter}
        onEnded={() => {
          syncMeter();
          save();
        }}
        onEmptied={syncMeter}
        onError={() => {
          // A broken file plays nothing, whatever its other flags say.
          watchMeter.stop(Date.now());
          setFailure((current) =>
            // Not overwritten: a named failure set above is more specific than
            // anything the element can report.
            current ?? { kind: "unplayable" },
          );
        }}
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

      {/* Two ways of knowing the same thing, and the earlier one is worth its
          own path: the codec the panel named is here before anything is fetched,
          so this line can be read *before* pressing play, while what the element
          reports arrives three seconds in and is the only answer for the many
          sources that declare nothing.

          Neither says anything when unsure. "unknown" is a browser that would
          not tell, and an unrecognised codec is a codec nobody thought of — a
          hedged line under a film whose sound is fine sends somebody to install
          an application over a problem they do not have. The apps line follows,
          as it does for mixed content, because here too it is the answer rather
          than a consolation. */}
      {(browserDecodes(audioCodec) === false || sound === "silent") && !failure ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("playerNoAudio")} {t("playerUseApps")}
        </p>
      ) : null}

      {failure ? (
        <div role="alert" className="border-destructive/40 mt-3 rounded-lg border px-4 py-3">
          <p className="font-medium">{t("playerFailedTitle")}</p>
          {failure.kind === "api" ? (
            <PlaybackRefusalMessage code={failure.code} sourceHref={sourceHref} />
          ) : (
            <p className="mt-1 text-sm">{t(bodyKey(failure.kind))}</p>
          )}
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
