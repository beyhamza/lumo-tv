"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { recordWatched } from "@/actions/playback";
import { PlaybackRefusalMessage } from "@/components/app/PlaybackRefusalMessage";
import { SourceDeleted } from "@/components/app/SourceDeleted";
import { useSourceGone } from "@/lib/playback/use-source-gone";

/**
 * Plays one live channel in the browser (US-11, ADR 0007).
 *
 * <h2>The stream is opened directly, or not at all</h2>
 *
 * ADR 0007: the browser opens the user's own IPTV server, and nothing is relayed
 * through our infrastructure. Two browser rules therefore decide whether this
 * works at all, and neither is ours to bend:
 *
 * - a page served over `https` cannot load an `http` sub-resource, full stop;
 * - `hls.js` fetches manifest and segments over XHR, so the panel must send
 *   `Access-Control-Allow-Origin`.
 *
 * Safari is the exception worth knowing: it plays HLS natively through
 * `<video src>`, which needs no CORS at all. So the same stream can work in
 * Safari and fail in Chrome, and the message below says which of the two
 * happened rather than reporting a generic failure — otherwise the next bug
 * report is "it works on my Mac".
 *
 * <h2>Failures are named</h2>
 *
 * A black `<video>` with no message is what makes someone conclude the product
 * is broken when it is their provider refusing. Every branch here ends in a
 * sentence that says what happened, and — when nothing can be done in a browser
 * — points at the applications, which have neither constraint.
 *
 * <h2>The source deleted while this plays</h2>
 *
 * A stream comes from the user's provider, so this page talks to nobody while it
 * plays and would never learn that the source was deleted from the phone. It
 * asks once a minute (`useSourceGone`); on a deletion **the server confirmed**
 * the stream is torn down, the player is replaced by one sentence and one
 * "Continue" back to the home page, and nothing else is started (US-024). An
 * outage is not a deletion and changes nothing here.
 */
export function ChannelPlayer({
  channelId,
  sourceId,
  name,
  quality,
  sourceHref,
  continueHref,
}: {
  channelId: string;
  /** The source this channel belongs to, for the once-a-minute existence check. */
  sourceId: string;
  name: string;
  quality?: string | null;
  /**
   * The source's own page, where a refusal that needs the source fixed — or a
   * refresh followed — sends the user. Built by the page, locale included: this
   * component renders under a provider that carries messages, not routing.
   */
  sourceHref: string;
  /** Where "Continue" goes once the source is gone: the home page. */
  continueHref: string;
}) {
  const t = useTranslations("App");
  const videoRef = useRef<HTMLVideoElement>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [maxConnections, setMaxConnections] = useState<number | null>(null);
  // A channel player is playing from the moment it is mounted: `?play=` is what
  // mounts it.
  const gone = useSourceGone(sourceId, true);

  useEffect(() => {
    // Part of this effect's dependencies on purpose: when the source is
    // confirmed gone, React runs the cleanup below — which destroys the hls.js
    // instance and stops the watchdog — and this early return keeps anything
    // from starting again. The `<video>` itself is unmounted by the render.
    if (gone) return;

    let disposed = false;
    let hls: { destroy: () => void } | null = null;

    /**
     * How long a black rectangle is allowed to last before it is explained.
     *
     * hls.js retries a refused manifest with backoff and only calls the failure
     * fatal at the end of that, which can be half a minute — and the native
     * Safari path reports some refusals not at all. Neither is an acceptable
     * amount of time to stare at nothing, so the component keeps its own clock:
     * if no frame has played by then, it says so.
     */
    const giveUpAfterMs = 12_000;
    const watchdog = setTimeout(() => {
      if (disposed) return;
      setFailure((current) => current ?? { kind: "blocked" });
    }, giveUpAfterMs);

    async function start() {
      setFailure(null);

      let payload: { url: string; maxConnections: number | null };
      try {
        // Fetched at the moment of playing, never rendered into the page: the
        // URL carries the user's panel credentials.
        const response = await fetch(`/api/playback/${channelId}`, {
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

      // Checked before anything is attempted, because the browser will not even
      // report this one usefully: it refuses the request outright, and the
      // console message never reaches the user.
      if (window.location.protocol === "https:" && payload.url.startsWith("http:")) {
        setFailure({ kind: "mixed-content" });
        return;
      }

      const video = videoRef.current;
      if (!video) return;

      // Loaded on demand rather than imported at the top: a catalogue page with
      // no player open should not carry a media library it never runs.
      const { default: Hls } = await import("hls.js");
      if (disposed) return;

      // MSE first, native second — and NOT the other way round, however much
      // "use the browser's own player when it has one" sounds right.
      //
      // `canPlayType("application/vnd.apple.mpegurl")` answers "maybe" in
      // Chromium, which does not play HLS at all. Trusting it sends Chrome down
      // the native path, where it fetches every segment, decodes none, and fires
      // no error — a black rectangle that ends only when the clock below runs
      // out. That is not a hypothesis: it is what the end-to-end run showed,
      // twelve and a half seconds at a time.
      //
      // The cost of this order: desktop Safari has MSE, so it goes through
      // hls.js too and needs CORS like everyone else. Only the browsers with no
      // MSE at all — iOS Safari among them — keep the native, CORS-free path.
      if (!Hls.isSupported()) {
        if (video.canPlayType("application/vnd.apple.mpegurl")) {
          video.src = payload.url;
          return;
        }
        setFailure({ kind: "unsupported" });
        return;
      }

      const instance = new Hls({
        enableWorker: true,
        // Short and explicit. hls.js defaults give a refused manifest several
        // retries over more than ten seconds, and for a panel that will never
        // answer — a missing CORS header is exactly that — those are ten seconds
        // of black rectangle.
        //
        // This is `manifestLoadPolicy`, not the `manifestLoadingMaxRetry` family:
        // those were deprecated in hls.js 1.x and are ignored, which is easy to
        // miss because setting them raises no error. The first version of this
        // used them, and the give-up clock below was silently doing all the work.
        manifestLoadPolicy: {
          default: {
            maxTimeToFirstByteMs: 3_000,
            maxLoadTimeMs: 5_000,
            timeoutRetry: { maxNumRetry: 1, retryDelayMs: 0, maxRetryDelayMs: 0 },
            errorRetry: { maxNumRetry: 1, retryDelayMs: 500, maxRetryDelayMs: 1_000 },
          },
        },
      });
      hls = instance;

      // hls.js reports a refused manifest as a NON-fatal error first and only
      // calls it fatal once its retries are spent. For a panel that will never
      // answer — a missing CORS header is exactly that — waiting for fatal meant
      // twelve seconds of black rectangle, which the watchdog below was quietly
      // covering up. Counting the attempts instead names it in about a second.
      let manifestRefusals = 0;

      instance.on(Hls.Events.ERROR, (_event, data) => {
        if (
          !data.fatal &&
          data.details === Hls.ErrorDetails.MANIFEST_LOAD_ERROR &&
          ++manifestRefusals > 1
        ) {
          setFailure({ kind: "blocked" });
          instance.destroy();
          return;
        }
        if (!data.fatal) return;
        setFailure(
          data.type === Hls.ErrorTypes.NETWORK_ERROR
            ? // Manifest or segment refused. From inside the page a CORS refusal
              // and an unreachable host are indistinguishable — the browser
              // reports both as an opaque network failure — so the message
              // covers both and does not guess.
              { kind: "blocked" }
            : { kind: "unplayable" },
        );
        instance.destroy();
      });
      instance.loadSource(payload.url);
      instance.attachMedia(video);
    }

    void start();

    return () => {
      disposed = true;
      clearTimeout(watchdog);
      hls?.destroy();
    };
  }, [channelId, gone]);

  if (gone) {
    return <SourceDeleted continueHref={continueHref} />;
  }

  return (
    <section
      className="border-border mb-8 rounded-xl border p-4"
      onKeyDown={(event) => onShortcut(event, videoRef.current)}
    >
      <div className="flex items-baseline gap-3">
        <h2 className="text-lg font-semibold tracking-tight">{name}</h2>
        {quality ? (
          <span className="border-border text-muted-foreground rounded border px-1.5 py-0.5 text-xs">
            {quality}
          </span>
        ) : null}
      </div>

      <video
        ref={videoRef}
        controls
        autoPlay
        playsInline
        // Recorded when playback actually starts, never when a channel is
        // merely focused: a "recently watched" rail built from what the cursor
        // passed over is the user's own history, made worse.
        onPlaying={() => {
          setFailure(null);
          void recordWatched(channelId);
        }}
        // The native path (Safari, iOS) reports a refusal here and nowhere
        // else: there is no hls.js instance to raise it.
        onError={() => setFailure((current) => current ?? { kind: "blocked" })}
        className="mt-3 aspect-video w-full rounded-lg bg-black"
      />

      {maxConnections ? (
        <p className="text-muted-foreground mt-2 text-sm">
          {t("playerMaxConnections", { count: maxConnections })}
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
              content or a missing CORS header, and telling someone to try again
              would be telling them to do the same thing twice. */}
          {failure.kind === "mixed-content" ||
          failure.kind === "blocked" ||
          failure.kind === "unsupported" ? (
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
  | { kind: "blocked" }
  | { kind: "unplayable" }
  | { kind: "unsupported" };

function bodyKey(kind: Exclude<Failure["kind"], "api">) {
  switch (kind) {
    case "mixed-content":
      return "playerMixedContent";
    case "blocked":
      return "playerBlocked";
    case "unplayable":
      return "playerUnplayable";
    default:
      return "playerUnsupported";
  }
}

/**
 * The two shortcuts the `<video>` element does not provide itself.
 *
 * Space, arrows and the rest come free with `controls` once the element has
 * focus; re-implementing them would only make them worse.
 */
function onShortcut(
  event: React.KeyboardEvent<HTMLElement>,
  video: HTMLVideoElement | null,
) {
  if (!video) return;

  if (event.key === "f") {
    event.preventDefault();
    if (document.fullscreenElement) void document.exitFullscreen();
    else void video.requestFullscreen();
  }
  if (event.key === "m") {
    event.preventDefault();
    video.muted = !video.muted;
  }
}
