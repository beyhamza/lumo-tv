package tv.lumo.android.core.player

/** The three things the options menu changes. */
enum class TrackKind { AUDIO, SUBTITLES, QUALITY }

/**
 * A track or quality change, from the tap to the player's answer (PS-07).
 *
 * The menu shows "Changing…" until Media3 reports the new selection. If it does
 * not — no answer within [TrackChangeRules.TIMEOUT_MS], or a player error while
 * it was being applied — the change failed: the previous selection is what the
 * menu shows (the player puts it back), with one message and one Retry. Nothing
 * retries by itself, and a failed change is never drawn as a player error.
 *
 * Pure and clocked from outside, so `TrackChangeTest` walks it with plain numbers.
 */
sealed interface TrackChange {

    data object Idle : TrackChange

    /**
     * @param target the id asked for; null is "Off" for subtitles, "Automatic" for quality.
     * @param previous the effective selection before the first tap, shown meanwhile.
     */
    data class Pending(
        val kind: TrackKind,
        val target: String?,
        val previous: String?,
        val startedAtMs: Long,
    ) : TrackChange

    /** Not applied. [previous] is still the effective selection; [target] is what Retry asks for again. */
    data class Failed(val kind: TrackKind, val target: String?, val previous: String?) : TrackChange
}

object TrackChangeRules {

    /**
     * How long the player has to answer. An HLS switch waits for the next
     * segment of the new rendition; ten seconds covers a slow one without
     * leaving somebody in front of "Changing…" for good.
     */
    const val TIMEOUT_MS: Long = 10_000L

    /**
     * What the player has actually selected for [kind], in the menu's terms:
     * a track id, or null for no subtitles / automatic quality.
     *
     * Quality is the one where "selected" in [TrackOptions] is the request, not
     * the answer — so it reads [VideoQuality.applied], and a manual quality is
     * effective only once it is the one variant Media3 is playing.
     */
    fun effective(options: TrackOptions, kind: TrackKind): String? = when (kind) {
        TrackKind.AUDIO -> options.audio.firstOrNull { it.selected }?.id
        TrackKind.SUBTITLES -> options.subtitles.firstOrNull { it.selected }?.id
        TrackKind.QUALITY ->
            if (options.qualityAuto) null else options.qualities.filter { it.applied }.singleOrNull()?.id
    }

    /**
     * A choice was made. A second choice of the same kind before the first was
     * answered replaces its target and keeps its [TrackChange.Pending.previous]:
     * that is still what is playing. Choosing what is already effective waits
     * for nothing.
     */
    fun start(
        current: TrackChange,
        kind: TrackKind,
        target: String?,
        options: TrackOptions,
        nowMs: Long,
    ): TrackChange {
        val effective = effective(options, kind)
        if (effective == target) return TrackChange.Idle
        val previous = (current as? TrackChange.Pending)?.takeIf { it.kind == kind }?.previous ?: effective
        return TrackChange.Pending(kind, target, previous, nowMs)
    }
}

/** The player published its tracks: confirmed when the target is effective, dropped with the stream. */
fun TrackChange.onOptions(options: TrackOptions): TrackChange = when {
    this == TrackChange.Idle -> this
    !options.known -> TrackChange.Idle
    this is TrackChange.Pending && TrackChangeRules.effective(options, kind) == target -> TrackChange.Idle
    else -> this
}

/** The player reported an error while the change was being applied. */
fun TrackChange.onFailure(): TrackChange =
    if (this is TrackChange.Pending) TrackChange.Failed(kind, target, previous) else this

/** Time passed: a change still unanswered after [timeoutMs] failed. */
fun TrackChange.onTick(nowMs: Long, timeoutMs: Long = TrackChangeRules.TIMEOUT_MS): TrackChange =
    if (this is TrackChange.Pending && nowMs - startedAtMs >= timeoutMs) {
        TrackChange.Failed(kind, target, previous)
    } else {
        this
    }

/** Retry, pressed by the viewer — once per press, never on its own. */
fun TrackChange.retried(nowMs: Long): TrackChange =
    if (this is TrackChange.Failed) TrackChange.Pending(kind, target, previous, nowMs) else this
