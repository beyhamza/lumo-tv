package tv.lumo.android.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * What a choice in the options menu does, beyond the player (S13-02).
 *
 * One per player screen, shared by the three that play something so the rule is
 * written once: the player changes track **now**, and a choice that names a
 * language becomes this device's preference for the next stream. A track without
 * a language and a quality are for this stream only (PS-06, Q6).
 *
 * <h2>Changing, then confirmed — or not (PS-07)</h2>
 *
 * A choice is a [TrackChange.Pending] until the player publishes it as the
 * effective selection; the menu says "Changing…" and keeps showing what is
 * playing. No answer within [timeoutMs], or a player error meanwhile, and the
 * change failed: the player puts the previous selection back and the menu offers
 * one Retry. Nothing is retried automatically. One change per kind at a time —
 * audio, subtitles and quality are answered independently.
 *
 * @param save writes the preference, true when it was written. False is told to
 *   the viewer — "applied for this playback, preference not saved" (PS-07) —
 *   rather than swallowed.
 * @param clock monotonic milliseconds; the tests pass the virtual time.
 */
class TrackChoices(
    private val player: LumoPlayer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val timeoutMs: Long = TrackChangeRules.TIMEOUT_MS,
    private val save: suspend (TrackPreferences) -> Boolean,
) {
    /** The preference the current stream was opened with, updated by choices. Null: none to keep. */
    private var preferences: TrackPreferences? = null

    private val _notSaved = MutableStateFlow(false)

    /** The last choice was applied but could not be remembered. */
    val notSaved: StateFlow<Boolean> = _notSaved.asStateFlow()

    private val _changes = MutableStateFlow<Map<TrackKind, TrackChange>>(emptyMap())

    /** The unanswered or failed change of each kind; a kind that is absent is settled. */
    val changes: StateFlow<Map<TrackKind, TrackChange>> = _changes.asStateFlow()

    private val watches = HashMap<TrackKind, Job>()

    /** A stream was opened with [preferences] (or none, for a screen without an account). */
    fun opened(preferences: TrackPreferences?) {
        this.preferences = preferences
        _notSaved.value = false
        watches.values.forEach { it.cancel() }
        watches.clear()
        _changes.value = emptyMap()
    }

    fun selectAudio(id: String) {
        val track = player.trackOptions.value.audio.firstOrNull { it.id == id && it.playable } ?: return
        begin(TrackKind.AUDIO, id)
        player.selectAudioTrack(id)
        remember { it.afterAudioChoice(track) }
    }

    /** [id] null is "Off". */
    fun selectSubtitle(id: String?) {
        val track = id?.let { wanted ->
            player.trackOptions.value.subtitles.firstOrNull { it.id == wanted && it.playable } ?: return
        }
        begin(TrackKind.SUBTITLES, id)
        player.selectSubtitleTrack(id)
        remember { it.afterSubtitleChoice(track) }
    }

    /** [id] null is "Automatic". For this stream only: never remembered. */
    fun selectQuality(id: String?) {
        if (id != null && player.trackOptions.value.qualities.none { it.id == id && it.playable }) return
        begin(TrackKind.QUALITY, id)
        player.selectVideoQuality(id)
    }

    /**
     * Retry of a failed change: asks the player once more, and waits once more.
     * The preference was already written by the first choice.
     */
    fun retry(kind: TrackKind) {
        val failed = _changes.value[kind] as? TrackChange.Failed ?: return
        set(kind, failed.retried(clock()))
        when (kind) {
            TrackKind.AUDIO -> failed.target?.let(player::selectAudioTrack)
            TrackKind.SUBTITLES -> player.selectSubtitleTrack(failed.target)
            TrackKind.QUALITY -> player.selectVideoQuality(failed.target)
        }
        watch(kind)
    }

    private fun begin(kind: TrackKind, target: String?) {
        val current = _changes.value[kind] ?: TrackChange.Idle
        val next = TrackChangeRules.start(current, kind, target, player.trackOptions.value, clock())
        set(kind, next)
        if (next is TrackChange.Pending) watch(kind) else watches.remove(kind)?.cancel()
    }

    /**
     * Waits for the player's answer to the change of [kind]: the options that
     * show it effective, a failure reported during it, or the timeout.
     */
    private fun watch(kind: TrackKind) {
        watches.remove(kind)?.cancel()
        watches[kind] = scope.launch {
            val answered = withTimeoutOrNull(timeoutMs) {
                merge(
                    player.trackOptions.map { options -> { change: TrackChange -> change.onOptions(options) } },
                    player.trackChangeFailures.filter { it == kind }.map { { change: TrackChange -> change.onFailure() } },
                ).first { step ->
                    val next = step(_changes.value[kind] ?: TrackChange.Idle)
                    set(kind, next)
                    next !is TrackChange.Pending
                }
            }
            if (answered == null) set(kind, (_changes.value[kind] ?: TrackChange.Idle).onTick(clock(), timeoutMs))
            when (_changes.value[kind] ?: TrackChange.Idle) {
                TrackChange.Idle -> player.settleTrackChange(kind)
                is TrackChange.Failed -> player.revertTrackChange(kind)
                is TrackChange.Pending -> Unit
            }
        }
    }

    private fun set(kind: TrackKind, change: TrackChange) = _changes.update {
        if (change == TrackChange.Idle) it - kind else it + (kind to change)
    }

    private fun remember(change: (TrackPreferences) -> TrackPreferences) {
        val current = preferences ?: return
        val updated = change(current)
        if (updated == current) return
        preferences = updated
        scope.launch { _notSaved.value = !save(updated) }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
