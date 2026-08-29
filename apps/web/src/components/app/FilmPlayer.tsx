"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";

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
 * <h2>Failures are named</h2>
 *
 * A black rectangle with no message is what makes someone conclude the product
 * is broken when it is their provider refusing. Every branch ends in a sentence,
 * and the ones a browser cannot get past point at the applications, which have
 * neither constraint.
 */
export function FilmPlayer({ filmId, name }: { filmId: string; name: string }) {
  const t = useTranslations("App");
  const tErrors = useTranslations("Errors");
  const videoRef = useRef<HTMLVideoElement>(null);

  const [failure, setFailure] = useState<Failure | null>(null);
  const [maxConnections, setMaxConnections] = useState<number | null>(null);
  const [seekable, setSeekable] = useState<boolean | null>(null);
  const [started, setStarted] = useState(false);

  useEffect(() => {
    if (!started) return;
    let disposed = false;

    async function start() {
      setFailure(null);

      let payload: { url: string; maxConnections: number | null };
      try {
        // Fetched at the moment of playing and never rendered into the page: the
        // URL carries the user's panel credentials.
        const response = await fetch(`/api/playback/vod/${filmId}`, {
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
  }, [filmId, started]);

  return (
    <section className="mt-6">
      {!started ? (
        <button
          type="button"
          onClick={() => setStarted(true)}
          className="bg-primary text-primary-foreground h-10 rounded-lg px-5 text-sm font-medium"
        >
          {t("filmsPlay")}
        </button>
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
    "UNAUTHENTICATED",
  ];
  return known.includes(code) ? tErrors(code) : t("playerUnplayable");
}
