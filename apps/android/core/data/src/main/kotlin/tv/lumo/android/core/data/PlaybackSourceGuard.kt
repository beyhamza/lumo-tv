package tv.lumo.android.core.data

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.repository.ActiveSourceRepository

/**
 * What a player does about a source deleted under it, said once for the three of
 * them (US-024).
 *
 * <h2>The sequence, and why each half waits for the other</h2>
 *
 * 1. While something plays, [PlaybackSourceWatcher] asks the server about the
 *    source. Only a `404 SOURCE_NOT_FOUND` ends the wait.
 * 2. On that proof the player is **stopped** and [deleted] turns true. The screen
 *    says "This source has been removed from your account." with one action.
 * 3. [acknowledge] — *Continue* — is what tells [ActiveSourceRepository] the
 *    source is gone, and not step 2. The resolution may open the shell's source
 *    chooser, which is a dialog: decided at step 2 it would cover the sentence
 *    that explains why the picture stopped. Decided on *Continue*, the viewer
 *    reads, presses, leaves the player, and *then* meets the one source left, the
 *    chooser, or "add a source". Nothing else is started on their behalf.
 *
 * <h2>Not a singleton</h2>
 *
 * One per player view model: it holds which source *that* player plays from.
 * Unscoped in Hilt, so each injection is a new one.
 */
class PlaybackSourceGuard @Inject constructor(
    private val watcher: PlaybackSourceWatcher,
    private val activeSource: ActiveSourceRepository,
) {

    private val _deleted = MutableStateFlow(false)

    /** True once the server has proven the deletion. Never on a network error. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    // No replay and no subscriber before [watch]: the `ON_START` that comes with
    // opening the player is dropped, which is wanted — the playback request has
    // just answered for this source.
    private val foregrounds = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var watching: Job? = null
    private var sourceId: String? = null

    /**
     * Starts watching [sourceId] for as long as [scope] lives.
     *
     * Idempotent per source: advancing to the next episode or the next channel
     * stays within one source and must not restart the 60-second clock each time.
     * A blank identifier — a deep link that carried none — watches nothing.
     *
     * @param onDeleted runs once, before [deleted] turns true: where the caller
     * stops its player, so that no frame shows the sentence over a moving picture.
     */
    fun watch(scope: CoroutineScope, sourceId: String, onDeleted: () -> Unit) {
        if (sourceId.isBlank() || sourceId == this.sourceId) return

        this.sourceId = sourceId
        watching?.cancel()
        watching = scope.launch {
            watcher.awaitDeletion(sourceId, foregrounds)
            onDeleted()
            _deleted.value = true
        }
    }

    /** The application is back in front of somebody: a reason to ask early. */
    fun onForeground() {
        foregrounds.tryEmit(Unit)
    }

    /** The player is closing. The sentence, once shown, stays until acknowledged. */
    fun stop() {
        watching?.cancel()
        watching = null
        if (!_deleted.value) sourceId = null
    }

    /**
     * *Continue*. Hands the proof to [ActiveSourceRepository], which selects the
     * one source left, asks when several are, or reports none.
     *
     * Suspends until that is decided, so that the caller leaves the player onto a
     * shell that already knows what it browses.
     */
    suspend fun acknowledge() {
        val gone = sourceId ?: return
        if (!_deleted.value) return
        activeSource.onSourceGone(gone)
    }
}
