import type { Track } from "../track-choice";
import {
  type AutoOnlyReason,
  type TrackController,
  type TrackListing,
  type TrackSnapshot,
  listeners,
} from "./types";

/**
 * Tracks of a bare `<video src>` — films and episodes, and channels on the
 * browsers without MSE (S13-01).
 *
 * <h2>What the element exposes, browser by browser</h2>
 *
 * - **Audio**: `HTMLMediaElement.audioTracks` is shipped by Safari only.
 *   Chromium keeps it behind a flag and Firefox behind a pref, so there the
 *   property is absent and the menu says the browser does not report the
 *   tracks of the file — not that there is only one.
 * - **Subtitles**: `textTracks` lists the text tracks the browser extracted
 *   from the file. Safari does it for MP4/HLS; Chromium and Firefox do not
 *   extract subtitles muxed into MP4 or MKV. An **empty** list therefore proves
 *   nothing, and is reported as "the browser does not tell", never "none".
 * - **Quality**: one progressive file is one encoding. Native HLS chooses its
 *   variant itself and exposes no control. Either way: Automatic only, with
 *   the reason.
 *
 * Identifiers are positions in the element's lists (`a0`, `t1`): they are
 * meaningless outside this element and this media, which is the point (PS-06).
 */
export function nativeTracks(
  video: HTMLVideoElement,
  quality: Extract<AutoOnlyReason, "single-file" | "browser-chooses">,
): TrackController {
  const subscribers = listeners();
  const audioList = (video as unknown as { audioTracks?: AudioTrackListLike }).audioTracks;
  const textList = video.textTracks;

  const onChange = () => subscribers.emit();
  audioList?.addEventListener?.("addtrack", onChange);
  audioList?.addEventListener?.("removetrack", onChange);
  audioList?.addEventListener?.("change", onChange);
  textList.addEventListener("addtrack", onChange);
  textList.addEventListener("removetrack", onChange);
  textList.addEventListener("change", onChange);
  video.addEventListener("loadedmetadata", onChange);

  function audioTracks(): AudioTrackLike[] {
    if (!audioList) return [];
    return Array.from({ length: audioList.length }, (_, index) => audioList[index]);
  }

  function textTracks(): { track: TextTrack; index: number }[] {
    return Array.from(textList)
      .map((track, index) => ({ track, index }))
      .filter(({ track }) => SUBTITLE_KINDS.has(track.kind));
  }

  function audio(): TrackListing {
    if (!audioList) return { state: "unknown", reason: "browser-hides-tracks" };
    const tracks = audioTracks();
    if (tracks.length === 0) {
      return { state: "unknown", reason: video.readyState >= 1 ? "browser-hides-tracks" : "loading" };
    }
    const listed: Track[] = tracks.map((track, index) => ({
      id: `a${index}`,
      language: track.language || null,
      name: track.label || null,
      isDefault: track.kind === "main",
      forced: false,
      supported: true,
    }));
    const selectedIndex = tracks.findIndex((track) => track.enabled);
    return { state: "listed", tracks: listed, selected: selectedIndex >= 0 ? `a${selectedIndex}` : null };
  }

  function subtitles(): TrackListing {
    const tracks = textTracks();
    if (tracks.length === 0) {
      return { state: "unknown", reason: video.readyState >= 1 ? "browser-hides-tracks" : "loading" };
    }
    const listed: Track[] = tracks.map(({ track, index }) => ({
      id: `t${index}`,
      language: track.language || null,
      name: track.label || null,
      isDefault: false,
      // WebKit reports a forced in-band track with its own kind.
      forced: (track.kind as string) === "forced",
      supported: true,
    }));
    const showing = tracks.find(({ track }) => track.mode === "showing");
    return { state: "listed", tracks: listed, selected: showing ? `t${showing.index}` : null };
  }

  const snapshotNow = (): TrackSnapshot => ({
    audio: audio(),
    subtitles: subtitles(),
    quality: { state: "auto-only", reason: quality },
  });

  // `useSyncExternalStore` wants the same object back while nothing changed.
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

  return {
    snapshot,
    subscribe: subscribers.add,
    async selectAudio(id) {
      const tracks = audioTracks();
      const target = Number(id.slice(1));
      if (!tracks[target]) return false;
      tracks.forEach((track, index) => {
        track.enabled = index === target;
      });
      // The element applies the change synchronously and fires `change`;
      // reading it back is the player's own answer.
      await nextTick();
      subscribers.emit();
      return Boolean(audioTracks()[target]?.enabled);
    },
    async selectSubtitles(id) {
      const tracks = textTracks();
      for (const { track, index } of tracks) {
        // "disabled", not "hidden": off means no text track selected at all,
        // a forced one included (Q6).
        track.mode = id === `t${index}` ? "showing" : "disabled";
      }
      await nextTick();
      subscribers.emit();
      return id === null
        ? tracks.every(({ track }) => track.mode !== "showing")
        : tracks.some(({ track, index }) => `t${index}` === id && track.mode === "showing");
    },
    async selectQuality(id) {
      // Nothing to choose: Automatic is the only answer, and only it succeeds.
      return id === "auto";
    },
    dispose() {
      audioList?.removeEventListener?.("addtrack", onChange);
      audioList?.removeEventListener?.("removetrack", onChange);
      audioList?.removeEventListener?.("change", onChange);
      textList.removeEventListener("addtrack", onChange);
      textList.removeEventListener("removetrack", onChange);
      textList.removeEventListener("change", onChange);
      video.removeEventListener("loadedmetadata", onChange);
      subscribers.clear();
    },
  };
}

const SUBTITLE_KINDS = new Set(["subtitles", "captions", "forced"]);

/** `AudioTrackList` is not in TypeScript's DOM library: only Safari ships it. */
type AudioTrackLike = { enabled: boolean; kind: string; label: string; language: string };
type AudioTrackListLike = {
  length: number;
  [index: number]: AudioTrackLike;
  addEventListener?: (type: string, listener: () => void) => void;
  removeEventListener?: (type: string, listener: () => void) => void;
};

function nextTick() {
  return new Promise<void>((resolve) => setTimeout(resolve, 0));
}
