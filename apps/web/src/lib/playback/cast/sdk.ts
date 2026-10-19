/**
 * Loading Google's Cast Web Sender, on demand (US-027, S16-02, ADR 0013 §5).
 *
 * Only a player calls this, so the script never reaches a marketing page or a
 * catalogue with no player open. It is Google's to serve — there is no package
 * of it — and it works only in Chromium browsers; elsewhere the promise
 * resolves to null and no button is drawn.
 *
 * The types below are the slice of the framework this application uses,
 * written by hand: Google publishes no typings with the script, and a type
 * claiming more than is called would be a promise nobody checks.
 */

const SENDER_URL = "https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1";

export type CastStateName = "NO_DEVICES_AVAILABLE" | "NOT_CONNECTED" | "CONNECTING" | "CONNECTED";

export type CastSession = {
  getCastDevice(): { friendlyName: string };
  loadMedia(request: unknown): Promise<unknown>;
  addMessageListener(namespace: string, listener: (namespace: string, message: string) => void): void;
  removeMessageListener(namespace: string, listener: (namespace: string, message: string) => void): void;
  endSession(stopCasting: boolean): void;
};

export type CastContext = {
  setOptions(options: { receiverApplicationId: string; autoJoinPolicy: string }): void;
  getCastState(): CastStateName;
  getCurrentSession(): CastSession | null;
  requestSession(): Promise<unknown>;
  addEventListener(type: string, listener: (event: { castState?: CastStateName }) => void): void;
  removeEventListener(type: string, listener: (event: { castState?: CastStateName }) => void): void;
};

export type RemotePlayer = {
  isConnected: boolean;
  isMediaLoaded: boolean;
  currentTime: number;
  duration: number;
  isPaused: boolean;
  playerState: string | null;
  canSeek: boolean;
  volumeLevel: number;
};

export type RemotePlayerController = {
  addEventListener(type: string, listener: (event: { field: string; value: unknown }) => void): void;
  removeEventListener(type: string, listener: (event: { field: string; value: unknown }) => void): void;
  playOrPause(): void;
  seek(): void;
  setVolumeLevel(): void;
  stop(): void;
};

/** The globals the script defines, as this application reads them. */
export type CastApi = {
  context: CastContext;
  player: RemotePlayer;
  controller: RemotePlayerController;
  /** Builds a load request for the Default Media Receiver. */
  loadRequest(media: {
    url: string;
    contentType: string;
    streamType: "LIVE" | "BUFFERED";
    title: string;
    posterUrl: string | null;
    startSeconds: number;
  }): unknown;
  events: { castState: string; anyChange: string };
  mediaNamespace: string;
};

type CastWindow = Window & {
  __onGCastApiAvailable?: (available: boolean) => void;
  cast?: any; // eslint-disable-line @typescript-eslint/no-explicit-any
  chrome?: any; // eslint-disable-line @typescript-eslint/no-explicit-any
};

let loading: Promise<CastApi | null> | null = null;

/** Loads the sender once per page life; null where Cast cannot work. */
export function loadCast(): Promise<CastApi | null> {
  if (typeof window === "undefined") return Promise.resolve(null);
  if (loading) return loading;
  loading = new Promise<CastApi | null>((resolve) => {
    const w = window as CastWindow;
    // Not Chromium, or no Presentation API: the script would load for nothing.
    if (!("chrome" in w) || !("PresentationRequest" in w)) {
      resolve(null);
      return;
    }
    w.__onGCastApiAvailable = (available) => resolve(available ? build(w) : null);
    const script = document.createElement("script");
    script.src = SENDER_URL;
    script.async = true;
    script.onerror = () => resolve(null);
    document.head.appendChild(script);
  });
  return loading;
}

function build(w: CastWindow): CastApi | null {
  const { cast, chrome } = w;
  if (!cast?.framework || !chrome?.cast?.media) return null;
  const context: CastContext = cast.framework.CastContext.getInstance();
  context.setOptions({
    receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID,
    // A session already running from this site — the next episode, another
    // tab — is joined rather than asked for again.
    autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED,
  });
  const player: RemotePlayer = new cast.framework.RemotePlayer();
  const controller: RemotePlayerController = new cast.framework.RemotePlayerController(player);
  return {
    context,
    player,
    controller,
    loadRequest(media) {
      const info = new chrome.cast.media.MediaInfo(media.url, media.contentType);
      info.contentUrl = media.url;
      info.streamType =
        media.streamType === "LIVE"
          ? chrome.cast.media.StreamType.LIVE
          : chrome.cast.media.StreamType.BUFFERED;
      const metadata = new chrome.cast.media.GenericMediaMetadata();
      metadata.title = media.title;
      if (media.posterUrl) metadata.images = [new chrome.cast.Image(media.posterUrl)];
      info.metadata = metadata;
      const request = new chrome.cast.media.LoadRequest(info);
      request.autoplay = true;
      request.currentTime = media.startSeconds;
      return request;
    },
    events: {
      castState: cast.framework.CastContextEventType.CAST_STATE_CHANGED,
      anyChange: cast.framework.RemotePlayerEventType.ANY_CHANGE,
    },
    mediaNamespace: "urn:x-cast:com.google.cast.media",
  };
}
