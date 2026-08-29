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
 * <h2>What it does not do yet, and it is deliberate</h2>
 *
 * **No resume, and no position saved.** That is `S6-08`, which has a decision in
 * it this file must not pre-empt: what a viewer wants to resume is a *series*, not
 * an episode — they remember having got to episode four, not an identifier — and
 * turning one into the other needs the tree. Writing half of it here would leave
 * positions saved that no screen reads.
 *
 * The film player's own saving is keyed on a film and a source; handing it an
 * episode would file an episode's position under `VOD`, which is a row the
 * contract says means something else.
 */
export function EpisodePlayer({
  episodeId,
  name,
}: {
  episodeId: string;
  name: string;
}) {
  return (
    <FilmPlayer
      filmId={episodeId}
      // Empty, and it is what switches the saving off — see the prop's own
      // documentation. An episode's position belongs to S6-08.
      sourceId=""
      name={name}
      resumeFromMs={0}
      resumeLabel={null}
      playbackPath="episode"
    />
  );
}
