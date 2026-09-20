package tv.lumo.android.core.data

import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.lumo.android.core.data.internal.ConnectivityMonitor
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * What one look at the server says about a source that is being played from.
 *
 * Three answers and not two, and the third is the point: **not knowing is not
 * "deleted"**. US-024 and c4-previous-catalogue.md §P6 accept exactly one proof
 * while a player is open — `404 SOURCE_NOT_FOUND` naming the source — and state
 * that a network error, a timeout or a `5xx` proves nothing. A player that
 * stopped on a Wi-Fi blink would punish somebody for their router.
 */
enum class SourcePresence {

    /** The server answered for this source: it exists. */
    Present,

    /** `404 SOURCE_NOT_FOUND`. The only answer that may stop playback. */
    Deleted,

    /** Offline, timed out, `5xx`, unreadable, any other code. Playback goes on. */
    Unknown,
}

/**
 * The decision, as a pure function of one call's result.
 *
 * The `else` branches are the rule rather than padding: every failure that is
 * not the one proof lands on [SourcePresence.Unknown], including codes this
 * build has never heard of.
 */
fun LumoResult<*>.asSourcePresence(): SourcePresence = when (this) {
    is LumoResult.Success -> SourcePresence.Present
    is LumoResult.Failure -> when (val error = error) {
        is LumoError.Api ->
            if (error.code == ErrorCode.SOURCE_NOT_FOUND) SourcePresence.Deleted else SourcePresence.Unknown

        else -> SourcePresence.Unknown
    }
}

/**
 * Notices, while something plays, that its source was deleted from another
 * device (US-024, C4 decision D5).
 *
 * <h2>Why playback needs a watcher at all</h2>
 *
 * Everywhere else the application learns of a deletion from the requests it
 * makes anyway. A player is the one place where it makes none: the stream comes
 * from the user's own server, and an hour of film is an hour without a word to
 * the API. So the question is asked on a clock — **every 60 seconds** — and at
 * the two moments a stale answer is most likely: the application coming back to
 * the foreground, and the network coming back.
 *
 * <h2>An existing call, naming the source</h2>
 *
 * `GET /sources/{id}`: no endpoint was added for this, and the `404` it answers
 * names the source, which the list cannot do when the list itself is
 * unreachable.
 *
 * <h2>In `core:data`, for the three players</h2>
 *
 * Live, film and episode players live in three features that may not import each
 * other. They share this, and what they do with the answer is the same three
 * lines: stop, say so, and let
 * [tv.lumo.android.core.data.repository.ActiveSourceRepository] decide what is
 * browsed next once the viewer has pressed Continue.
 */
class PlaybackSourceWatcher internal constructor(
    private val check: suspend (sourceId: String) -> LumoResult<*>,
    private val reconnections: Flow<Unit>,
    private val intervalMillis: Long = CHECK_EVERY_MILLIS,
) {

    @Inject
    internal constructor(sources: SourceRepository, connectivity: ConnectivityMonitor) : this(
        check = { sourceId -> sources.source(sourceId) },
        reconnections = connectivity.regained,
    )

    /**
     * Suspends until the server **proves** the source is gone, then returns.
     *
     * It never returns for any other reason: cancel the coroutine to stop
     * watching — leaving the player does. The first check happens after one
     * interval rather than at once, because the playback request that opened the
     * player has just answered for this source.
     *
     * @param foregrounds one emission each time the application comes back in
     * front of somebody. The screen owns that signal — it is the one holding a
     * lifecycle — and the network's return is observed here.
     */
    suspend fun awaitDeletion(sourceId: String, foregrounds: Flow<Unit> = emptyFlow()) {
        // Conflated: three signals while a check is in flight are one reason to
        // check again, not three.
        val wakeUps = Channel<Unit>(Channel.CONFLATED)

        coroutineScope {
            val listening = launch {
                merge(foregrounds, reconnections).collect { wakeUps.trySend(Unit) }
            }

            try {
                while (true) {
                    // Whichever comes first: the clock, or a reason to ask early.
                    withTimeoutOrNull(intervalMillis) { wakeUps.receive() }

                    if (check(sourceId).asSourcePresence() == SourcePresence.Deleted) break
                }
            } finally {
                listening.cancel()
            }
        }
    }

    companion object {
        /** C4, decision D5. The product's number, not a tuning constant. */
        const val CHECK_EVERY_MILLIS: Long = 60_000L
    }
}
