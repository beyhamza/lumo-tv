import type Hls from "hls.js";
import type { Track } from "../track-choice";
import {
  SWITCH_TIMEOUT_MS,
  type QualityListing,
  type TrackController,
  type TrackListing,
  type TrackSnapshot,
  listeners,
} from "./types";

/**
 * Tracks of an HLS stream played through hls.js — the channel player (S13-01).
 *
 * hls.js reads the multivariant playlist, so here the lists are **known**:
 *
 * - **Audio**: the `EXT-X-MEDIA TYPE=AUDIO` renditions. None declared means the
 *   audio is muxed into the video segments, where hls.js plays the first audio
 *   stream and exposes no others — said as such, not as "no other track".
 * - **Subtitles**: the `TYPE=SUBTITLES` (WebVTT) renditions. An empty list is a
 *   real "none declared". CEA-608/708 captions carried inside the video are not
 *   offered: hls.js surfaces them as anonymous text tracks of the element, and
 *   they are left to the browser.
 * - **Quality**: the variants hls.js kept (it drops those whose codecs this
 *   browser cannot decode). Automatic is `currentLevel = -1`.
 *
 * Identifiers are hls.js indexes in the current stream (PS-06).
 */
export function hlsTracks(hls: Hls, events: typeof Hls.Events): TrackController {
  const subscribers = listeners();
  let parsed = false;

  const onUpdate = () => subscribers.emit();
  const onParsed = () => {
    parsed = true;
    subscribers.emit();
  };
  hls.on(events.MANIFEST_PARSED, onParsed);
  hls.on(events.AUDIO_TRACKS_UPDATED, onUpdate);
  hls.on(events.SUBTITLE_TRACKS_UPDATED, onUpdate);
  hls.on(events.LEVELS_UPDATED, onUpdate);
  hls.on(events.AUDIO_TRACK_SWITCHED, onUpdate);
  hls.on(events.SUBTITLE_TRACK_SWITCH, onUpdate);
  hls.on(events.LEVEL_SWITCHED, onUpdate);

  function audio(): TrackListing {
    if (!parsed) return { state: "unknown", reason: "loading" };
    const tracks = hls.audioTracks;
    if (tracks.length === 0) return { state: "unknown", reason: "not-declared" };
    return {
      state: "listed",
      tracks: tracks.map(
        (track, index): Track => ({
          id: String(index),
          language: track.lang ?? null,
          name: track.name || null,
          isDefault: track.default,
          forced: false,
          supported: true,
        }),
      ),
      selected: hls.audioTrack >= 0 ? String(hls.audioTrack) : null,
    };
  }

  function subtitles(): TrackListing {
    if (!parsed) return { state: "unknown", reason: "loading" };
    return {
      state: "listed",
      tracks: hls.subtitleTracks.map(
        (track, index): Track => ({
          id: String(index),
          language: track.lang ?? null,
          name: track.name || null,
          isDefault: track.default,
          forced: track.forced,
          supported: true,
        }),
      ),
      selected: hls.subtitleTrack >= 0 ? String(hls.subtitleTrack) : null,
    };
  }

  function quality(): QualityListing {
    if (!parsed) return { state: "auto-only", reason: "loading" };
    const levels = hls.levels;
    if (levels.length < 2) return { state: "auto-only", reason: "single-variant" };
    return {
      state: "listed",
      levels: levels.map((level, index) => ({
        id: String(index),
        height: level.height || null,
        bitrate: level.bitrate || null,
      })),
      selected: hls.autoLevelEnabled ? "auto" : String(hls.manualLevel),
    };
  }

  const snapshotNow = (): TrackSnapshot => ({
    audio: audio(),
    subtitles: subtitles(),
    quality: quality(),
  });
  let cached = snapshotNow();
  let cachedKey = JSON.stringify(cached);
  const snapshot = () => {
    const next = snapshotNow();
    const key = JSON.stringify(next);
    if (key !== cachedKey) {
      cached = next;
      cachedKey = key;
    }
    return cached;
  };

  /** Resolves when `check` holds after one of `on` fires, false on timeout or error. */
  function waitFor(on: (typeof events)[keyof typeof events][], check: () => boolean) {
    return new Promise<boolean>((resolve) => {
      if (check()) {
        resolve(true);
        return;
      }
      const done = (result: boolean) => {
        clearTimeout(timer);
        for (const event of on) hls.off(event, handler);
        hls.off(events.ERROR, onError);
        resolve(result);
      };
      const handler = () => {
        if (check()) done(true);
      };
      const onError = (_event: unknown, data: { fatal: boolean; details: string }) => {
        if (data.fatal || /TRACK_LOAD|LEVEL_LOAD|LEVEL_SWITCH/.test(String(data.details).toUpperCase())) {
          done(false);
        }
      };
      const timer = setTimeout(() => done(false), SWITCH_TIMEOUT_MS);
      for (const event of on) hls.on(event, handler);
      hls.on(events.ERROR, onError);
    });
  }

  return {
    snapshot,
    subscribe: subscribers.add,
    selectAudio(id) {
      const index = Number(id);
      if (!hls.audioTracks[index]) return Promise.resolve(false);
      hls.audioTrack = index;
      return waitFor([events.AUDIO_TRACK_SWITCHED], () => hls.audioTrack === index);
    },
    selectSubtitles(id) {
      const index = id === null ? -1 : Number(id);
      if (index >= 0 && !hls.subtitleTracks[index]) return Promise.resolve(false);
      hls.subtitleDisplay = true;
      hls.subtitleTrack = index;
      return waitFor([events.SUBTITLE_TRACK_SWITCH], () => hls.subtitleTrack === index);
    },
    selectQuality(id) {
      if (id === "auto") {
        hls.currentLevel = -1;
        return Promise.resolve(hls.autoLevelEnabled);
      }
      const index = Number(id);
      if (!hls.levels[index]) return Promise.resolve(false);
      hls.currentLevel = index;
      return waitFor([events.LEVEL_SWITCHED], () => hls.currentLevel === index);
    },
    dispose() {
      hls.off(events.MANIFEST_PARSED, onParsed);
      hls.off(events.AUDIO_TRACKS_UPDATED, onUpdate);
      hls.off(events.SUBTITLE_TRACKS_UPDATED, onUpdate);
      hls.off(events.LEVELS_UPDATED, onUpdate);
      hls.off(events.AUDIO_TRACK_SWITCHED, onUpdate);
      hls.off(events.SUBTITLE_TRACK_SWITCH, onUpdate);
      hls.off(events.LEVEL_SWITCHED, onUpdate);
      subscribers.clear();
    },
  };
}
