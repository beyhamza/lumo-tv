package tv.lumo.android.core.player

import kotlinx.coroutines.flow.first

/**
 * Seeks to [positionMs] as soon as the stream just handed to [LumoPlayer.play] says
 * whether it can be moved through (S12-05, US-019).
 *
 * `seekTo` is ignored while seeking is [SeekAvailability.UNKNOWN] — which it always
 * is right after `play`, before the first bytes arrive — so a resume asked for at
 * that moment was silently dropped and every "Resume at…" started from zero. This
 * waits for the answer: available, the position is applied; refused or live, it is
 * not, which is the honest outcome — a server that will not serve part of a file
 * cannot resume one either.
 *
 * Call it from a coroutine cancelled with the stream (a new `play`, `stop`).
 *
 * @return whether the seek was applied.
 */
suspend fun LumoPlayer.seekWhenSeekable(positionMs: Long): Boolean {
    if (positionMs <= 0L) return false
    val seek = progress.first { it.seek != SeekAvailability.UNKNOWN }.seek
    if (seek != SeekAvailability.AVAILABLE) return false
    seekTo(positionMs)
    return true
}
