package tv.lumo.android.feature.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.SyncOutcome
import tv.lumo.android.core.data.asSyncOutcome
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.data.repository.SourceRemover
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.repository.VodRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.SourceKind

/**
 * "My sources": the account's sources, and what can be done to each (US-024).
 *
 * <h2>One view model, two surfaces, and the television uses half of it</h2>
 *
 * The phone manages: use, rename, refresh, automatic refresh, delete. The
 * television chooses and refreshes, and says where the rest is done. The rules
 * they share — what a refused refresh means, which number is known, what happens
 * to the active source — are here once, so that a phone and a television cannot
 * come to disagree about them (AGENTS.md §2).
 *
 * <h2>The list is [ActiveSourceRepository]'s, not a second copy</h2>
 *
 * The shell's switcher, the three grids and this screen all read the same
 * `GET /sources`, through the same singleton. A list of its own here would be
 * able to show a source as ready while the switcher above it still said
 * refreshing. So every action ends by asking the repository to read again, and
 * what is drawn follows [ActiveSourceRepository.state].
 *
 * <h2>What is not here</h2>
 *
 * The interval at which a running synchronisation is asked about again belongs to
 * the screens, which poll from the composition — a view model outlives the screen
 * it serves, and would keep a radio awake behind a film. And nothing here decides
 * what is browsed after a deletion: `SourceRemover` hands that to the repository.
 */
@HiltViewModel
class MySourcesViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val vod: VodRepository,
    private val series: SeriesRepository,
    private val activeSource: ActiveSourceRepository,
    private val remover: SourceRemover,
) : ViewModel() {

    private val _state = MutableStateFlow(MySourcesState())
    val state: StateFlow<MySourcesState> = _state.asStateFlow()

    /** The `last_synced_at` each source had when its totals were last read. */
    private val countedAt = mutableMapOf<String, OffsetDateTime>()

    init {
        // Resolved a while ago: a state that was true ten minutes ago is the one
        // thing this list must not show. Still loading means it is being asked.
        if (activeSource.state.value !is ActiveSourceState.Loading) reload()

        viewModelScope.launch {
            activeSource.state.collect { active ->
                _state.update { it.following(active) }
                loadCounts()
            }
        }
    }

    /**
     * Reads the list again. Also "Try again" when nothing could be read, and what
     * the screens call every few seconds while a synchronisation is on display.
     */
    fun reload() {
        viewModelScope.launch { activeSource.refresh() }
    }

    /**
     * Films and series, for the sources worth asking — see [sourcesToCount].
     *
     * One short request each (two for a panel), against a listing the server
     * answers while the source refreshes (C4, P1). A playlist is never asked for
     * series: it cannot carry any (`adr/0010`), and the line is not drawn.
     */
    private fun loadCounts() {
        sourcesToCount(_state.value.sources, countedAt).forEach { source ->
            val id = source.id.toString()
            // Stamped before the requests: the list re-emits every few seconds
            // during a refresh, and must not start them again each time.
            source.lastSyncedAt?.let { countedAt[id] = it }

            viewModelScope.launch {
                val counts = ContentCounts(
                    films = vod.filmCount(id),
                    series = if (source.kind == SourceKind.XTREAM) series.seriesCount(id) else null,
                )
                _state.update { it.copy(counts = it.counts + (id to counts)) }
            }
        }
    }

    // ---- use ----------------------------------------------------------------

    /** Browses this source, on this device, from now on. No confirmation (US-018). */
    fun use(sourceId: String) {
        viewModelScope.launch { activeSource.select(sourceId) }
    }

    // ---- refresh ------------------------------------------------------------

    /**
     * *Refresh*, and *Try again* after a failed import — the same request.
     *
     * A press while one is running does nothing: the control is disabled, and
     * this guard is for the press that beat the recomposition.
     */
    fun refresh(sourceId: String) {
        val row = _state.value.rows.firstOrNull { it.id == sourceId } ?: return
        if (row.refreshing) return

        _state.update { it.copy(refreshes = it.refreshes + (sourceId to RefreshControl.Requesting)) }

        viewModelScope.launch {
            val outcome = sources.sync(sourceId).asSyncOutcome()
            _state.update { it.afterSync(sourceId, outcome) }

            when (outcome) {
                // Deleted elsewhere: a proof, and the repository decides what is
                // browsed next (US-018). The row leaves with the list.
                SyncOutcome.Gone -> activeSource.onSourceGone(sourceId)
                // The switcher and the grids read the status from the repository.
                is SyncOutcome.Accepted, SyncOutcome.AlreadyRunning -> activeSource.refresh()
                is SyncOutcome.RateLimited, is SyncOutcome.Failed -> Unit
            }
        }
    }

    // ---- automatic refresh --------------------------------------------------

    /**
     * Turns automatic refresh on or off **for this source**, on the whole account:
     * the setting is the source's, run by the server, and a change made here is
     * the one the website and the television see (US-024).
     *
     * `auto_sync` is the one property of `PATCH /sources/{id}` that leaves the
     * catalogue alone, so this never restarts an ingestion.
     */
    fun setAutoSync(sourceId: String, enabled: Boolean) {
        if (sourceId in _state.value.pendingAutoSync) return

        _state.update { it.togglingAutoSync(sourceId, enabled) }

        viewModelScope.launch {
            val updated = sources.update(sourceId, autoSync = enabled).valueOrNull()
            _state.update { it.autoSyncSettled(sourceId, updated) }
        }
    }

    // ---- rename -------------------------------------------------------------

    fun askRename(sourceId: String) {
        val row = _state.value.rows.firstOrNull { it.id == sourceId } ?: return
        _state.update { it.copy(dialog = SourceDialog.Rename(sourceId, row.label, value = row.label)) }
    }

    fun onRenameChange(value: String) = _state.update { current ->
        val dialog = current.dialog as? SourceDialog.Rename ?: return@update current
        current.copy(dialog = dialog.copy(value = value, failed = false))
    }

    /**
     * Sends the name and nothing else: any other property in a `PATCH` would
     * restart the import (see `SourceRepository.update`).
     */
    fun confirmRename() {
        val dialog = _state.value.dialog as? SourceDialog.Rename ?: return
        if (!dialog.canSave) return

        _state.update { it.copy(dialog = dialog.copy(saving = true, failed = false)) }

        viewModelScope.launch {
            when (sources.update(dialog.sourceId, label = dialog.value.trim())) {
                is LumoResult.Success -> {
                    _state.update { it.copy(dialog = null) }
                    // The name is also what the switcher shows.
                    activeSource.refresh()
                }

                is LumoResult.Failure -> _state.update { current ->
                    val open = current.dialog as? SourceDialog.Rename ?: return@update current
                    current.copy(dialog = open.copy(saving = false, failed = true))
                }
            }
        }
    }

    // ---- delete -------------------------------------------------------------

    fun askDelete(sourceId: String) {
        val row = _state.value.rows.firstOrNull { it.id == sourceId } ?: return
        _state.update { it.copy(dialog = SourceDialog.Delete(sourceId, row.label)) }
    }

    /**
     * Deletes, after the dialog has named the source and what goes with it.
     *
     * `SourceRemover` holds the order: the server first, then the device's cache
     * of that source, then the active source re-decided. **A refusal changes
     * nothing here** — the dialog stays open, says so, and the source is still in
     * the list behind it.
     */
    fun confirmDelete() {
        val dialog = _state.value.dialog as? SourceDialog.Delete ?: return
        if (dialog.deleting) return

        _state.update { it.copy(dialog = dialog.copy(deleting = true, failure = null)) }

        viewModelScope.launch {
            when (val result = remover.remove(dialog.sourceId)) {
                is LumoResult.Success -> {
                    countedAt.remove(dialog.sourceId)
                    _state.update { it.copy(dialog = null) }
                }

                is LumoResult.Failure -> _state.update { current ->
                    val open = current.dialog as? SourceDialog.Delete ?: return@update current
                    current.copy(dialog = open.copy(deleting = false, failure = result.error))
                }
            }
        }
    }

    /** Cancel, Back, or a tap outside. Not while a write is in flight. */
    fun dismissDialog() = _state.update { current ->
        val busy = when (val dialog = current.dialog) {
            is SourceDialog.Rename -> dialog.saving
            is SourceDialog.Delete -> dialog.deleting
            null -> false
        }
        if (busy) current else current.copy(dialog = null)
    }
}
