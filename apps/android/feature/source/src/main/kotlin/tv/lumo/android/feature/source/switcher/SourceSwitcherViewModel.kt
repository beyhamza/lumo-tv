package tv.lumo.android.feature.source.switcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.selectedSourceId
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * The source switcher of both shells (US-018, design `S8-E04`).
 *
 * <h2>Why it lives in `feature:source` and is placed by the applications</h2>
 *
 * It has to be visible from every section, which makes it part of the shell; and
 * it is state and UI, which the application modules are not allowed to hold. So
 * the view model and the two composables live here, the shells only decide where
 * they go — and no feature has to depend on another to show it.
 *
 * <h2>It owns no state of its own</h2>
 *
 * Everything drawn comes from [ActiveSourceRepository], which is also what the
 * catalogue screens read. A switcher that kept its own idea of the active source
 * would be able to show a check mark next to one source while the grid behind it
 * showed another.
 */
@HiltViewModel
class SourceSwitcherViewModel @Inject constructor(
    private val activeSource: ActiveSourceRepository,
) : ViewModel() {

    val state: StateFlow<SourceSwitcherState> = activeSource.state
        .map(::switcherStateOf)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = switcherStateOf(activeSource.state.value),
        )

    /** See [sourceSwitches]. The shells pop a detail screen of the old source on it. */
    val switches: Flow<String> = activeSource.state.sourceSwitches()

    /** See [lastSourceLosses]. The shells open "add a source" on it. */
    val lastSourceLost: Flow<Unit> = activeSource.state.lastSourceLosses()

    /** Applied at once, with no confirmation (US-018). */
    fun onSourceChosen(sourceId: String) {
        viewModelScope.launch { activeSource.select(sourceId) }
    }

    /**
     * The list is about to be read by somebody, so it is read from the server
     * first: a state — ready, refreshing, in error — that was true ten minutes ago
     * is the one thing this list must not show.
     */
    fun onListOpened() {
        viewModelScope.launch { activeSource.refresh() }
    }

    /**
     * The application is back in front of somebody.
     *
     * One of the moments the product names for noticing a deletion made elsewhere
     * (c4-previous-catalogue.md §P6): a source removed from the website while the
     * television slept is learnt here, from the list, rather than from the first
     * request that fails. A failed fetch changes nothing, as everywhere.
     *
     * Skipped while the repository is still resolving: that is the launch, and
     * the request this would make is already in flight.
     */
    fun onForeground() {
        if (activeSource.state.value is ActiveSourceState.Loading) return
        viewModelScope.launch { activeSource.refresh() }
    }

    private companion object {
        /** Survives a rotation without re-subscribing; does not outlive the screen. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * One emission each time the device starts browsing **another** source.
 *
 * What a shell needs in order to leave a film or a series that belongs to the
 * source that was just left (US-018). Three things it must not fire on, and each
 * is a bug that would look like the application navigating by itself:
 *
 * - **the first answer** — opening the application is not a switch;
 * - **a refresh** — the same source re-read is the same source;
 * - **the states in between** — a deletion elsewhere goes through "several left,
 *   choose one" before it lands, and only the landing is a switch. The detail
 *   screen underneath is covered by a blocking chooser meanwhile.
 */
internal fun Flow<ActiveSourceState>.sourceSwitches(): Flow<String> =
    mapNotNull { it.selectedSourceId }
        .distinctUntilChanged()
        .drop(1)

/**
 * One emission when an account that had sources is found to have none left.
 *
 * The last source was deleted — here, or from another device and noticed here —
 * and US-018 sends the user back to "add a source". Launching on an account that
 * never had one is not this: `AppStartDecision` already opens on the form, and a
 * second navigation on top of it would be the application fidgeting.
 *
 * [ActiveSourceState.Loading] forgets: it is a signed-out device or another
 * account arriving, and what the previous account had says nothing about this one.
 */
internal fun Flow<ActiveSourceState>.lastSourceLosses(): Flow<Unit> = flow {
    var hadSources = false

    collect { active ->
        when (active) {
            is ActiveSourceState.Selected, is ActiveSourceState.NeedsChoice -> hadSources = true
            ActiveSourceState.Loading -> hadSources = false
            ActiveSourceState.None -> if (hadSources) {
                hadSources = false
                emit(Unit)
            }
            // Unknown is not empty: an unreachable server proves nothing.
            ActiveSourceState.Unavailable -> Unit
        }
    }
}

/**
 * What the switcher draws.
 *
 * @param activeLabel the name shown next to the switcher, or null when there is
 * nothing to name: no source, several and no choice yet, or a choice remembered
 * offline whose name only the server knows.
 * @param mustChoose several sources and no valid choice on this device. The list
 * is then presented as required, and nothing in it is preselected.
 */
data class SourceSwitcherState(
    val activeLabel: String? = null,
    val choices: List<SourceChoice> = emptyList(),
    val mustChoose: Boolean = false,
) {

    /**
     * Whether there is anything to switch to.
     *
     * With a single source its name is plain text, with no affordance that
     * suggests a list (US-018): a control that opens onto one entry, already
     * ticked, is a control somebody presses once to learn it does nothing.
     */
    val canSwitch: Boolean
        get() = choices.size > 1
}

data class SourceChoice(
    val id: String,
    val label: String,
    val status: SourceChoiceStatus,
    val active: Boolean,
)

/**
 * The three states the switcher names (US-018), and the one it does not.
 *
 * `PENDING` and `SYNCING` are one state, as they are on the source's own screen:
 * accepted versus started is the server's distinction, not one a viewer can act
 * on.
 */
enum class SourceChoiceStatus {
    Ready,
    Refreshing,
    Failed,

    /**
     * A status this build does not know. `SourceStatus` can gain a value within
     * v1, and an older application meeting a newer server says nothing rather
     * than something false.
     */
    Unknown,
}

/** A pure function of the repository's answer, so every case can be pinned by a test. */
internal fun switcherStateOf(active: ActiveSourceState): SourceSwitcherState = when (active) {
    is ActiveSourceState.Selected -> SourceSwitcherState(
        activeLabel = active.source?.label,
        choices = active.sources.map { it.asChoice(activeId = active.sourceId) },
    )

    is ActiveSourceState.NeedsChoice -> SourceSwitcherState(
        choices = active.sources.map { it.asChoice(activeId = null) },
        mustChoose = true,
    )

    else -> SourceSwitcherState()
}

private fun Source.asChoice(activeId: String?) = SourceChoice(
    id = id.toString(),
    label = label,
    status = when (status) {
        SourceStatus.READY -> SourceChoiceStatus.Ready
        SourceStatus.PENDING, SourceStatus.SYNCING -> SourceChoiceStatus.Refreshing
        SourceStatus.ERROR -> SourceChoiceStatus.Failed
        else -> SourceChoiceStatus.Unknown
    },
    active = id.toString() == activeId,
)
