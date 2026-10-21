package tv.lumo.android.core.player.cast

/**
 * What the receiver said, reduced to what the sender has to do about it (S16-05).
 *
 * The S15-00 report lists four signals of a refusal, all of which end in the same
 * sentence — "Ce flux ne peut pas être envoyé sur ce Chromecast" — and a return to
 * the phone at the position reached (sprint 16, decision 4). Two other signals look
 * like failures and are not: a `LOAD` replaced by another one (`904
 * LOAD_INTERRUPTED`, `CANCELED`, `REPLACED`), and the device picker closed without a
 * choice. Those say nothing.
 *
 * The codes are the Cast SDK's values, written out so this file stays a JVM test;
 * `core:cast` checks them against the SDK's own constants.
 */
sealed interface CastSignal {

    /** 1 — the answer to `RemoteMediaClient.load()`. `0` is success. */
    data class LoadAnswered(val statusCode: Int) : CastSignal

    /** 2 — `RemoteMediaClient.Callback.onMediaError`: the receiver's detailed error. */
    data class MediaError(val detailedErrorCode: Int?, val type: String?) : CastSignal

    /** 3 — a status saying the receiver went idle, and why. */
    data class WentIdle(val reason: RemoteIdleReason) : CastSignal

    /** 4 — neither playback nor an error, [CastSignals.SILENCE_TIMEOUT_MS] after the `LOAD`. */
    data object Silence : CastSignal

    /** The session could not be started: the picker was closed, or the device did not answer. */
    data class SessionStartFailed(val statusCode: Int) : CastSignal
}

enum class RemoteIdleReason { NONE, FINISHED, CANCELED, INTERRUPTED, ERROR }

/** What the sender does with a [CastSignal]. */
sealed interface CastVerdict {

    /** Not a failure, or not this stream's: nothing is shown. */
    data object Ignore : CastVerdict

    /** The stream played to its end on the receiver: an episode may follow (S13-03). */
    data object Finished : CastVerdict

    /** Stopped from elsewhere (the TV's remote, another phone): offer to resume here. */
    data object StoppedElsewhere : CastVerdict

    /**
     * Refused. [code] is what gets logged — a short label and a number, never the
     * URL (AGENTS.md §5) — for the device campaign of S17.
     */
    data class Refused(val code: String) : CastVerdict
}

object CastSignals {

    /** Signal 4: the delay the S15-00 report proposes. */
    const val SILENCE_TIMEOUT_MS = 15_000L

    // CastStatusCodes, as the SDK defines them.
    const val SUCCESS = 0
    const val INTERRUPTED = 14
    const val CANCELED = 2002
    const val REPLACED = 2103

    // MediaError.DetailedErrorCode.LOAD_INTERRUPTED.
    const val LOAD_INTERRUPTED = 904

    /**
     * @param started whether the receiver has played this stream at all. An
     *   `IDLE`/`FINISHED` before that is not an end: nothing was watched.
     */
    fun verdict(signal: CastSignal, started: Boolean): CastVerdict = when (signal) {
        is CastSignal.LoadAnswered -> when (signal.statusCode) {
            SUCCESS, INTERRUPTED, CANCELED, REPLACED -> CastVerdict.Ignore
            else -> CastVerdict.Refused("load:${signal.statusCode}")
        }

        is CastSignal.MediaError ->
            if (signal.detailedErrorCode == LOAD_INTERRUPTED) {
                CastVerdict.Ignore
            } else {
                CastVerdict.Refused("media:${signal.type ?: "?"}:${signal.detailedErrorCode ?: "?"}")
            }

        is CastSignal.WentIdle -> when (signal.reason) {
            RemoteIdleReason.ERROR -> CastVerdict.Refused("idle:error")
            RemoteIdleReason.FINISHED -> if (started) CastVerdict.Finished else CastVerdict.Refused("idle:finished-before-start")
            RemoteIdleReason.CANCELED -> if (started) CastVerdict.StoppedElsewhere else CastVerdict.Ignore
            RemoteIdleReason.INTERRUPTED, RemoteIdleReason.NONE -> CastVerdict.Ignore
        }

        CastSignal.Silence -> if (started) CastVerdict.Ignore else CastVerdict.Refused("silence")

        // Nothing was sent: the stream was never offered to the device. The local
        // playback has not been touched yet either (it stops only before the LOAD).
        is CastSignal.SessionStartFailed -> CastVerdict.Ignore
    }
}
