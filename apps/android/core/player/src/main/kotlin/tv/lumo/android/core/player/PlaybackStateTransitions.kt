package tv.lumo.android.core.player

import androidx.media3.common.Player

/**
 * The [PlaybackState] that follows one of ExoPlayer's `onPlaybackStateChanged`
 * callbacks, given the state that was current when it arrived.
 *
 * <h2>Why this takes the current state at all</h2>
 *
 * ExoPlayer reports a failure twice, in this order: `onPlayerError`, then
 * `onPlaybackStateChanged(STATE_IDLE)` — the player has stopped, and it says so.
 * Mapped in isolation, that second callback reads as "nothing is playing" and
 * overwrites the [PlaybackState.Failed] the first one had just published. The
 * screens see `Failed` for a frame, then `Idle`, and draw the latter: a black
 * panel, no message, no *Retry* — the very thing US-09 asks the player never to
 * show. Every one of the six players goes through here, so every one of them
 * had it.
 *
 * So an idle report is only a new [PlaybackState.Idle] when nothing has failed.
 * A failure stays on screen until something explicit replaces it: [LumoPlayer.stop]
 * when the viewer leaves, or [LumoPlayer.play] when they retry — both of which
 * write the state directly rather than through this function.
 *
 * Pure, and `internal` rather than private to the listener, so it can be held
 * to that under test without an [androidx.media3.exoplayer.ExoPlayer].
 */
internal fun PlaybackState.afterExoPlayerState(
    playbackState: Int,
    playWhenReady: Boolean,
    title: String?,
): PlaybackState = when (playbackState) {
    Player.STATE_BUFFERING -> PlaybackState.Buffering
    Player.STATE_READY -> if (playWhenReady) PlaybackState.Playing(title) else PlaybackState.Paused
    Player.STATE_ENDED -> PlaybackState.Ended
    // STATE_IDLE, and anything a future Media3 adds: a failure already on screen
    // outranks a player that has merely gone quiet.
    else -> if (this is PlaybackState.Failed) this else PlaybackState.Idle
}
