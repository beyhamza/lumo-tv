package tv.lumo.android.feature.series

import tv.lumo.android.core.data.model.Episode
import tv.lumo.android.core.data.model.EpisodeProgress

/**
 * The offer at the real end of an episode (S13-03), as pure functions.
 *
 * The state machine is small and it is the half of the feature nobody forgives
 * getting wrong, so it is written here and tested without a player:
 *
 * ```
 *   end reached ──(no next)──────────────► back to the series sheet, no countdown (CW-15)
 *        │
 *        ├─(next, autoplay on)──► counting n…1 ──(0)──► play next
 *        │                          │
 *        │                          └─(any interaction)──► offer without countdown
 *        └─(next, autoplay off)─────────────────────────► offer without countdown (CW-14)
 * ```
 *
 * **Only the real end** reaches this: the player's `Ended`, never the 95 %
 * threshold, which marks an episode watched but does not start the next (CW-08).
 */

/**
 * What to offer once an episode has ended, or null when there is nothing after it.
 *
 * @param nextProgress the next episode's saved position, if any. Started and not
 *   finished: it resumes there (CW-12). Finished: from the beginning, keeping the
 *   order of the series (resume-player.md, "Suivant déjà terminé").
 * @param countdownSeconds 10 on a television, 5 on a phone. Zero means no countdown.
 */
internal fun upNextOffer(
    next: Episode?,
    nextProgress: EpisodeProgress?,
    autoplay: Boolean,
    countdownSeconds: Int,
): UpNext? {
    next ?: return null
    val resumeAt = nextProgress
        ?.takeIf { it.episodeId == next.id && !it.finished && it.positionMs > 0L }
        ?.positionMs
    return UpNext(
        episode = next,
        secondsLeft = countdownSeconds.takeIf { autoplay && it > 0 },
        resumeAtMs = resumeAt,
    )
}

/** One second of the countdown. Null stays null: a cancelled countdown never resumes. */
internal fun UpNext.tick(): UpNext = copy(secondsLeft = secondsLeft?.let { (it - 1).coerceAtLeast(0) })

/** The viewer touched the controls: the countdown stops, the offer stays (CW-13). */
internal fun UpNext.cancelled(): UpNext = copy(secondsLeft = null)

/** The countdown has run out and nobody touched anything. */
internal val UpNext.due: Boolean get() = secondsLeft == 0
