"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  CAST_SILENCE_MS,
  type CastKind,
  type CastSignal,
  castMediaShape,
  castRefusalLog,
  isCastRefusal,
} from "@/lib/playback/cast/media";
import { type CastApi, type CastSession, loadCast } from "@/lib/playback/cast/sdk";

/** What a player shows about Cast. */
export type CastView =
  /** No Cast here (not Chromium, no device on the network, a source with a User-Agent): no button. */
  | { phase: "none" }
  /** A device is on the network: the button is drawn. */
  | { phase: "available" }
  /** Asking for a device, or loading on it. */
  | { phase: "connecting"; device: string | null }
  | {
      phase: "casting";
      device: string;
      positionMs: number;
      durationMs: number | null;
      paused: boolean;
      canSeek: boolean;
      volume: number;
    };

export type CastMedia = {
  url: string;
  kind: CastKind;
  title: string;
  posterUrl: string | null;
  startMs: number;
};

export type CastCallbacks = {
  /** Every change on the receiver, for saving and for the watch meter (ADR 0012). */
  onProgress(progress: { positionMs: number; durationMs: number | null; playing: boolean }): void;
  /** The file ended on the receiver. */
  onEnded(): void;
  /** The receiver refused the stream: playback goes on here, from `atMs`. */
  onRefused(atMs: number): void;
  /** Casting stopped, by the user or by the receiver going away, at `atMs`. */
  onStopped(atMs: number): void;
};

/**
 * Sending the current playback to a Chromecast from a web player (US-027,
 * S16-02 to S16-05).
 *
 * The page keeps playing locally until a device is chosen; then the player is
 * asked for the media (`getMedia`), which is the moment it pauses — a
 * single-connection subscription cannot feed two players (decision 5). Every
 * refusal ends the session and hands the position back (decision 4). Leaving
 * the page does **not** end a session: the next episode is loaded on the
 * receiver by the next page (`hasSession`).
 */
export function useCast({ enabled, callbacks }: { enabled: boolean; callbacks: CastCallbacks }) {
  const [view, setView] = useState<CastView>({ phase: "none" });
  const [refused, setRefused] = useState(false);
  const api = useRef<CastApi | null>(null);
  const handlers = useRef(callbacks);
  // The latest callbacks, without re-subscribing to the SDK on every render.
  useEffect(() => {
    handlers.current = callbacks;
  });

  /** The load being watched: cleared once it plays, refuses or is replaced. */
  const pending = useRef<{ timer: ReturnType<typeof setTimeout>; startMs: number } | null>(null);
  const casting = useRef(false);
  const lastMs = useRef(0);
  const listening = useRef<{ session: CastSession; listener: (ns: string, m: string) => void } | null>(
    null,
  );

  const detach = useCallback(() => {
    if (pending.current) clearTimeout(pending.current.timer);
    pending.current = null;
    if (listening.current) {
      listening.current.session.removeMessageListener(
        api.current?.mediaNamespace ?? "",
        listening.current.listener,
      );
      listening.current = null;
    }
  }, []);

  const idleView = useCallback((): CastView => {
    const state = api.current?.context.getCastState();
    return state && state !== "NO_DEVICES_AVAILABLE" ? { phase: "available" } : { phase: "none" };
  }, []);

  const refuse = useCallback(
    (signal: CastSignal) => {
      if (!isCastRefusal(signal)) return;
      // The code helps the device run of S17; the URL carries credentials.
      console.warn(castRefusalLog(signal));
      const atMs = pending.current?.startMs ?? lastMs.current;
      detach();
      casting.current = false;
      api.current?.context.getCurrentSession()?.endSession(true);
      setRefused(true);
      setView(idleView());
      handlers.current.onRefused(atMs);
    },
    [detach, idleView],
  );

  // Loads the sender and follows the devices and the remote player.
  useEffect(() => {
    if (!enabled) return;
    let disposed = false;
    let cleanup = () => {};
    void loadCast().then((loaded) => {
      if (disposed || !loaded) return;
      api.current = loaded;
      const { context, player, controller, events } = loaded;

      const onState = () => {
        if (!casting.current && !pending.current) setView(idleView());
      };
      const onChange = () => {
        if (!casting.current && !pending.current) return;
        if (!player.isConnected) {
          // The receiver went away, or another sender ended the session. While
          // a load is still pending the player may not be connected yet: the
          // silence timer covers that case.
          if (!casting.current) return;
          const atMs = lastMs.current;
          detach();
          casting.current = false;
          setView(idleView());
          handlers.current.onStopped(atMs);
          return;
        }
        const positionMs = Math.round(player.currentTime * 1000);
        const durationMs = player.duration > 0 ? Math.round(player.duration * 1000) : null;
        const playing = player.playerState === "PLAYING";
        if (playing && pending.current) {
          clearTimeout(pending.current.timer);
          pending.current = null;
          casting.current = true;
        }
        if (!casting.current) return;
        if (player.isMediaLoaded) lastMs.current = positionMs;
        setView({
          phase: "casting",
          device: context.getCurrentSession()?.getCastDevice().friendlyName ?? "Chromecast",
          positionMs: lastMs.current,
          durationMs,
          paused: player.isPaused,
          canSeek: player.canSeek,
          volume: player.volumeLevel,
        });
        handlers.current.onProgress({ positionMs: lastMs.current, durationMs, playing });
      };

      context.addEventListener(events.castState, onState);
      controller.addEventListener(events.anyChange, onChange);
      onState();
      cleanup = () => {
        context.removeEventListener(events.castState, onState);
        controller.removeEventListener(events.anyChange, onChange);
      };
    });
    return () => {
      disposed = true;
      cleanup();
      // The session outlives the page on purpose (next episode); only what
      // this page was listening to goes.
      detach();
    };
  }, [enabled, detach, idleView]);

  /** Whether a session from this site is already running — the next episode goes there. */
  const hasSession = useCallback(() => Boolean(api.current?.context.getCurrentSession()), []);

  /**
   * Opens Chrome's device picker if no session runs, then loads the media.
   * `getMedia` is called once a device is chosen; it pauses the local player.
   */
  const cast = useCallback(
    async (getMedia: () => CastMedia | null) => {
      const loaded = api.current ?? (await loadCast());
      if (!loaded) return;
      api.current = loaded;
      setRefused(false);
      if (!loaded.context.getCurrentSession()) {
        setView({ phase: "connecting", device: null });
        try {
          await loaded.context.requestSession();
        } catch {
          // The picker closed, or no device answered: not a refusal of the stream.
          setView(idleView());
          return;
        }
      }
      const session = loaded.context.getCurrentSession();
      const media = getMedia();
      if (!session || !media) {
        setView(idleView());
        return;
      }
      const device = session.getCastDevice().friendlyName;
      setView({ phase: "connecting", device });
      detach();
      lastMs.current = media.startMs;

      const listener = (_ns: string, raw: string) => {
        let message: { type?: string; detailedErrorCode?: number; status?: { idleReason?: string; playerState?: string }[] };
        try {
          message = JSON.parse(raw);
        } catch {
          return;
        }
        if (message.type === "MEDIA_STATUS") {
          const status = message.status?.[0];
          if (status?.playerState !== "IDLE") return;
          if (status.idleReason === "FINISHED" && casting.current) {
            handlers.current.onEnded();
            return;
          }
          refuse({ kind: "idle", reason: status.idleReason ?? null });
          return;
        }
        if (message.type) {
          refuse({
            kind: "media-error",
            type: message.type,
            detailedErrorCode: message.detailedErrorCode ?? null,
          });
        }
      };
      session.addMessageListener(loaded.mediaNamespace, listener);
      listening.current = { session, listener };
      pending.current = {
        startMs: media.startMs,
        timer: setTimeout(() => refuse({ kind: "silence" }), CAST_SILENCE_MS),
      };

      const shape = castMediaShape(media.url, media.kind);
      try {
        await session.loadMedia(
          loaded.loadRequest({
            url: media.url,
            contentType: shape.contentType,
            streamType: shape.streamType,
            title: media.title,
            posterUrl: media.posterUrl,
            startSeconds: media.kind === "channel" ? 0 : media.startMs / 1000,
          }),
        );
      } catch (error) {
        refuse({ kind: "load-rejected", code: typeof error === "string" ? error : null });
      }
    },
    [detach, idleView, refuse],
  );

  const playOrPause = useCallback(() => api.current?.controller.playOrPause(), []);

  const seekBy = useCallback((seconds: number) => {
    const loaded = api.current;
    if (!loaded || !loaded.player.canSeek) return;
    const target = loaded.player.currentTime + seconds;
    loaded.player.currentTime = Math.max(
      0,
      loaded.player.duration > 0 ? Math.min(target, loaded.player.duration) : target,
    );
    loaded.controller.seek();
  }, []);

  const setVolume = useCallback((level: number) => {
    const loaded = api.current;
    if (!loaded) return;
    loaded.player.volumeLevel = level;
    loaded.controller.setVolumeLevel();
  }, []);

  /** Stops casting and hands back where the receiver was. */
  const stop = useCallback(() => {
    const atMs = lastMs.current;
    detach();
    casting.current = false;
    api.current?.context.getCurrentSession()?.endSession(true);
    setView(idleView());
    handlers.current.onStopped(atMs);
  }, [detach, idleView]);

  return {
    // Off (no URL yet, a source with a User-Agent): no button, whatever the SDK says.
    view: enabled ? view : ({ phase: "none" } as CastView),
    refused,     dismissRefusal: () => setRefused(false),
    hasSession,
    cast,
    playOrPause,
    seekBy,
    setVolume,
    stop,
  };
}
