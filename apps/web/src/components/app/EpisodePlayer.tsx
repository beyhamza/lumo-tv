"use client";

import { FilmPlayer } from "@/components/app/FilmPlayer";

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
 * Nothing about that turn is here. This component plays a file and records where it
 * got to; the page around it decided which episode and from where.
 */
export function EpisodePlayer({
  episodeId,
  sourceId,
  name,
  resumeFromMs,
  resumeLabel,
  audioCodec,
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
   * Where to start, **chosen on the page around this component**. Zero is the
   * beginning, and it is somebody's answer to a question they were asked.
   */
  resumeFromMs: number;
  /** "Resume at 20:14", or null when there is nothing to resume. */
  resumeLabel: string | null;
}) {
  return (
    <FilmPlayer
      filmId={episodeId}
      sourceId={sourceId}
      name={name}
      resumeFromMs={resumeFromMs}
      resumeLabel={resumeLabel}
      playbackPath="episode"
      itemType="EPISODE"
      audioCodec={audioCodec}
    />
  );
}