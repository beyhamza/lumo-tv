package tv.lumo.android.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * What a choice in the options menu does, beyond the player (S13-02).
 *
 * One per player screen, shared by the three that play something so the rule is
 * written once: the player changes track **now**, and a choice that names a
 * language becomes this device's preference for the next stream. A track without
 * a language and a quality are for this stream only (PS-06, Q6).
 *
 * @param save writes the preference, true when it was written. False is told to
 *   the viewer — "applied for this playback, preference not saved" (PS-07) —
 *   rather than swallowed.
 */
class TrackChoices(
    private val player: LumoPlayer,
    private val scope: CoroutineScope,
    private val save: suspend (TrackPreferences) -> Boolean,
) {
    /** The preference the current stream was opened with, updated by choices. Null: none to keep. */
    private var preferences: TrackPreferences? = null

    private val _notSaved = MutableStateFlow(false)

    /** The last choice was applied but could not be remembered. */
    val notSaved: StateFlow<Boolean> = _notSaved.asStateFlow()

    /** A stream was opened with [preferences] (or none, for a screen without an account). */
    fun opened(preferences: TrackPreferences?) {
        this.preferences = preferences
        _notSaved.value = false
    }

    fun selectAudio(id: String) {
        val track = player.trackOptions.value.audio.firstOrNull { it.id == id && it.playable } ?: return
        player.selectAudioTrack(id)
        remember { it.afterAudioChoice(track) }
    }

    /** [id] null is "Off". */
    fun selectSubtitle(id: String?) {
        val track = id?.let { wanted ->
            player.trackOptions.value.subtitles.firstOrNull { it.id == wanted && it.playable } ?: return
        }
        player.selectSubtitleTrack(id)
        remember { it.afterSubtitleChoice(track) }
    }

    /** [id] null is "Automatic". For this stream only: never remembered. */
    fun selectQuality(id: String?) = player.selectVideoQuality(id)

    private fun remember(change: (TrackPreferences) -> TrackPreferences) {
        val current = preferences ?: return
        val updated = change(current)
        if (updated == current) return
        preferences = updated
        scope.launch { _notSaved.value = !save(updated) }
    }
}
