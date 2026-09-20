/**
 * What a player says when the API refuses to hand over a stream, and which way
 * out it offers (US-024 "Lecture et délai", contract lot C4 — P2).
 *
 * <h2>One list for the three players</h2>
 *
 * The channel player and the film player each had their own copy of "the codes I
 * have a sentence for", one code apart. C4 adds a refusal to all three playback
 * operations at once, which is the moment two copies become two places to
 * forget.
 *
 * <h2>The source-state refusals, and what each one asks of the user</h2>
 *
 * | Code | What happened | Way out |
 * |---|---|---|
 * | `SOURCE_NOT_READY` | a refresh is running — since C4 this is the only way a browsable catalogue refuses playback | **follow** it; playback resumes when it ends |
 * | `SOURCE_AUTH_FAILED` | the provider refused the credentials at the last refresh | **fix** the source |
 * | `SOURCE_EXPIRED` | the subscription with the provider ran out | **fix** the source |
 * | `SOURCE_MAX_CONNECTIONS` | the panel is at its stream limit | none here — close another stream |
 *
 * `SOURCE_NOT_READY` does **not** reuse the `Errors` sentence ("has not finished
 * importing"): at playback that wording is wrong since C4. The catalogue on
 * screen is the proof that an import finished; what is running is a refresh.
 *
 * The link goes to the source's page because that is where the progress, the
 * retry and the management live. This module only says *that* there is a way
 * out and of which kind; the component owns the href.
 *
 * <h2>Unknown codes degrade</h2>
 *
 * The contract allows new codes within v1, so anything not listed falls back to
 * the generic "could not be played" rather than rendering a key.
 */
export type PlaybackRefusal = {
  message:
    | { namespace: "Errors"; key: RefusalErrorKey }
    | { namespace: "App"; key: "playerSourceRefreshing" | "playerUnplayable" };
  /** A link to the source's page, and what it is for. */
  sourceLink: "follow" | "fix" | null;
};

/** The `Errors` messages a refusal can use. Every one exists in both locales. */
export type RefusalErrorKey =
  | "SOURCE_AUTH_FAILED"
  | "SOURCE_EXPIRED"
  | "SOURCE_MAX_CONNECTIONS"
  | "SOURCE_NOT_FOUND"
  | "CHANNEL_NOT_FOUND"
  | "VOD_ITEM_NOT_FOUND"
  | "EPISODE_NOT_FOUND"
  | "UNAUTHENTICATED"
  | "network";

export function playbackRefusal(code: string): PlaybackRefusal {
  switch (code) {
    case "SOURCE_NOT_READY":
      return { message: { namespace: "App", key: "playerSourceRefreshing" }, sourceLink: "follow" };
    case "SOURCE_AUTH_FAILED":
    case "SOURCE_EXPIRED":
      return { message: { namespace: "Errors", key: code }, sourceLink: "fix" };
    case "SOURCE_MAX_CONNECTIONS":
    case "SOURCE_NOT_FOUND":
    case "CHANNEL_NOT_FOUND":
    case "VOD_ITEM_NOT_FOUND":
    case "EPISODE_NOT_FOUND":
    case "UNAUTHENTICATED":
      return { message: { namespace: "Errors", key: code }, sourceLink: null };
    // The players' own sentinel for a route handler that could not be reached.
    case "NETWORK":
      return { message: { namespace: "Errors", key: "network" }, sourceLink: null };
    default:
      return { message: { namespace: "App", key: "playerUnplayable" }, sourceLink: null };
  }
}
