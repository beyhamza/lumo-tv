package tv.lumo.android.feature.source

import java.time.OffsetDateTime
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.SyncOutcome
import tv.lumo.android.core.data.knownSources
import tv.lumo.android.core.data.retryAfterMinutes
import tv.lumo.android.core.data.selectedSourceId
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * "My sources" (US-024, design S8-E05 to S8-E09), as values.
 *
 * Everything the two surfaces draw is a pure function of what is in this file,
 * for the reason `SourceView` gives: the interesting half of the screen — which
 * number is known, which source is active, what a refused refresh says — can then
 * be held by tests that need no coroutine, no clock and no server. The view model
 * fetches and calls these.
 */

/**
 * Films and series of one source, **when known**.
 *
 * `Source` carries `channel_count` and nothing for films or series, by decision
 * (c4-previous-catalogue.md §P3): these come from the paginated listings' totals,
 * asked with one row. Null is "not known" — not asked yet, could not be asked, or
 * never ingested — and is **never drawn as zero**.
 */
data class ContentCounts(val films: Int? = null, val series: Int? = null)

/** What one source is doing, in the three states a viewer can tell apart. */
sealed interface SourceRowState {

    data object Ready : SourceRowState

    /** `PENDING` or `SYNCING`, with the server's real step. Never a percentage. */
    data class Refreshing(val step: SyncStep?) : SourceRowState

    /**
     * `ERROR`, with the reason, its age, and the one way out that can help — the
     * four exits of S2-09, unchanged: a retry offered to somebody whose password
     * was refused is a button they press until they give up.
     */
    data class Failed(
        val reason: IngestionErrorCode?,
        val at: OffsetDateTime?,
        val exit: SourceExit,
    ) : SourceRowState

    /** A status newer than this build. Said as nothing rather than as something false. */
    data object Unknown : SourceRowState
}

/**
 * What the *Refresh* control of one source says right now.
 *
 * Apart from [SourceRowState] because it is about **this device's request**, not
 * about the source: two people can look at the same refreshing source and only
 * one of them was told to wait.
 */
sealed interface RefreshControl {

    data object Idle : RefreshControl

    /** `POST /sources/{id}/sync` is in flight. The control is disabled meanwhile. */
    data object Requesting : RefreshControl

    /**
     * `429 SOURCE_SYNC_RATE_LIMITED`.
     *
     * @param minutes the server's `Retry-After`, rounded up to whole minutes, or
     * null when it sent none this build can read. The sentence then has no number:
     * **a duration is never invented** (US-024).
     */
    data class Wait(val minutes: Int?) : RefreshControl

    /** The request failed for another reason. The source keeps the state it had. */
    data class Failed(val offline: Boolean) : RefreshControl
}

/** One line of the list. */
data class SourceRow(
    val id: String,
    val label: String,
    val kind: SourceKind,
    /** The source this device browses (US-018). It carries the mark, and no "Use" action. */
    val active: Boolean,
    val state: SourceRowState,
    /** The last **successful** synchronisation. Null when there never was one. */
    val lastSyncedAt: OffsetDateTime?,
    val channels: Int?,
    val films: Int?,
    /**
     * Always null for a playlist, and [showsSeries] says the line is not drawn at
     * all: an M3U source cannot carry series (`adr/0010`), and "0 series" under one
     * would present a property of the format as a property of the subscription.
     */
    val series: Int?,
    val autoSync: Boolean,
    /** A write to `auto_sync` is in flight; the switch waits for it. */
    val autoSyncPending: Boolean,
    val refresh: RefreshControl,
) {
    val showsSeries: Boolean get() = kind == SourceKind.XTREAM

    /**
     * Whether *Refresh* reads "Refreshing…" and takes no press: a synchronisation
     * is running on the server, or this device's request for one has not answered.
     * One control, one operation at a time (US-024).
     */
    val refreshing: Boolean
        get() = state is SourceRowState.Refreshing || refresh == RefreshControl.Requesting
}

/** The two dialogs of the phone. At most one at a time, hence one field. */
sealed interface SourceDialog {

    /** @param failed the server refused the name; the dialog stays open and says so. */
    data class Rename(
        val sourceId: String,
        val currentLabel: String,
        val value: String,
        val saving: Boolean = false,
        val failed: Boolean = false,
    ) : SourceDialog {
        /** The empty-field check and nothing more: the rules are the server's. */
        val canSave: Boolean get() = !saving && value.isNotBlank() && value.trim() != currentLabel
    }

    /**
     * @param failure why the deletion did not happen. **Nothing has changed on the
     * device in that case** — see `SourceRemover` — and the dialog stays open with
     * the source still there.
     */
    data class Delete(
        val sourceId: String,
        val label: String,
        val deleting: Boolean = false,
        val failure: LumoError? = null,
    ) : SourceDialog
}

/** Where the list stands. */
enum class MySourcesPhase {
    Loading,

    /** The list could not be read and nothing is known. Worth retrying; not "no source". */
    Unavailable,

    /** The server said the account has no source. The way forward is adding one. */
    Empty,

    Listed,
}

/**
 * What the view model holds. The rows are **derived**, never stored: an
 * optimistic switch, a pending refresh and a counter that just arrived all land in
 * the same list without anybody having to remember to rebuild it.
 */
data class MySourcesState(
    val phase: MySourcesPhase = MySourcesPhase.Loading,
    val sources: List<Source> = emptyList(),
    val activeId: String? = null,
    val counts: Map<String, ContentCounts> = emptyMap(),
    /**
     * Switches whose write has not come back, and what they show meanwhile. Read
     * before the server's value and dropped when the call settles, either way.
     */
    val pendingAutoSync: Map<String, Boolean> = emptyMap(),
    val refreshes: Map<String, RefreshControl> = emptyMap(),
    val dialog: SourceDialog? = null,
    /** The last `auto_sync` write that was refused, by source. Cleared on the next try. */
    val autoSyncFailed: Set<String> = emptySet(),
) {
    val rows: List<SourceRow>
        get() = sourceRowsOf(sources, activeId, counts, pendingAutoSync, refreshes)

    /** Whether anything on the list is moving, which is when the list is asked again. */
    val anyRefreshing: Boolean
        get() = sources.any { it.status == SourceStatus.PENDING || it.status == SourceStatus.SYNCING }

    /** This list, as [ActiveSourceState] now describes the account. */
    fun following(active: ActiveSourceState): MySourcesState = when (active) {
        ActiveSourceState.Loading -> copy(phase = MySourcesPhase.Loading)

        ActiveSourceState.None -> copy(
            phase = MySourcesPhase.Empty,
            sources = emptyList(),
            activeId = null,
        )

        // Unknown is not empty: an unreachable server proves nothing (US-018).
        ActiveSourceState.Unavailable -> copy(phase = MySourcesPhase.Unavailable)

        is ActiveSourceState.NeedsChoice, is ActiveSourceState.Selected -> {
            val known = active.knownSources
            if (known.isEmpty()) {
                // A choice remembered offline: an identifier, and no list to draw.
                copy(phase = MySourcesPhase.Unavailable, activeId = active.selectedSourceId)
            } else {
                val ids = known.map { it.id.toString() }.toSet()
                copy(
                    phase = MySourcesPhase.Listed,
                    sources = known,
                    activeId = active.selectedSourceId,
                    // What belonged to a source that is gone goes with it.
                    counts = counts.filterKeys { it in ids },
                    pendingAutoSync = pendingAutoSync.filterKeys { it in ids },
                    refreshes = refreshes.filterKeys { it in ids },
                    autoSyncFailed = autoSyncFailed.filter { it in ids }.toSet(),
                )
            }
        }
    }

    /**
     * The outcome of a manual refresh, on the control that asked for it.
     *
     * Accepted and already-running are the same thing to a viewer — it is
     * refreshing — and leave the control idle: the row's own state, read from the
     * server, is what says "Refreshing…" from here on.
     */
    fun afterSync(sourceId: String, outcome: SyncOutcome): MySourcesState {
        val control = when (outcome) {
            is SyncOutcome.Accepted, SyncOutcome.AlreadyRunning, SyncOutcome.Gone -> RefreshControl.Idle
            is SyncOutcome.RateLimited -> RefreshControl.Wait(retryAfterMinutes(outcome.retryAfterSeconds))
            is SyncOutcome.Failed -> RefreshControl.Failed(offline = outcome.error is LumoError.Offline)
        }
        val accepted = (outcome as? SyncOutcome.Accepted)?.source

        return copy(
            refreshes = refreshes + (sourceId to control),
            // The `202` carries the source already `PENDING`: shown at once, so the
            // control does not flick back to "Refresh" before the first poll.
            sources = if (accepted == null) sources else sources.map {
                if (it.id == accepted.id) accepted else it
            },
        )
    }

    /** A switch moved: optimistic, for **that source only**. */
    fun togglingAutoSync(sourceId: String, enabled: Boolean): MySourcesState = copy(
        pendingAutoSync = pendingAutoSync + (sourceId to enabled),
        autoSyncFailed = autoSyncFailed - sourceId,
    )

    /**
     * The write came back. On success the server's source replaces the old one; on
     * failure the optimistic value is dropped, which puts the switch back where the
     * server has it, and the row says the change was not saved.
     */
    fun autoSyncSettled(sourceId: String, updated: Source?): MySourcesState = copy(
        pendingAutoSync = pendingAutoSync - sourceId,
        autoSyncFailed = if (updated == null) autoSyncFailed + sourceId else autoSyncFailed - sourceId,
        sources = if (updated == null) sources else sources.map {
            if (it.id == updated.id) updated else it
        },
    )
}

/**
 * The list, from what is known.
 *
 * The server's order is kept: it is the order the switcher shows, and two lists of
 * the same sources in two orders would read as two different accounts.
 */
internal fun sourceRowsOf(
    sources: List<Source>,
    activeId: String?,
    counts: Map<String, ContentCounts> = emptyMap(),
    pendingAutoSync: Map<String, Boolean> = emptyMap(),
    refreshes: Map<String, RefreshControl> = emptyMap(),
): List<SourceRow> = sources.map { source ->
    val id = source.id.toString()
    val known = counts[id]

    SourceRow(
        id = id,
        label = source.label,
        kind = source.kind,
        active = id == activeId,
        state = when (source.status) {
            SourceStatus.READY -> SourceRowState.Ready
            SourceStatus.PENDING, SourceStatus.SYNCING -> SourceRowState.Refreshing(source.syncStep)
            SourceStatus.ERROR -> SourceRowState.Failed(
                reason = source.errorCode,
                at = source.lastErrorAt,
                exit = exitFor(source.errorCode),
            )
            else -> SourceRowState.Unknown
        },
        lastSyncedAt = source.lastSyncedAt,
        // Null until one ingestion has succeeded (C4, P3) — and drawn as absent.
        channels = source.channelCount,
        films = known?.films,
        series = known?.series.takeIf { source.kind == SourceKind.XTREAM },
        autoSync = pendingAutoSync[id] ?: source.autoSync,
        autoSyncPending = id in pendingAutoSync,
        refresh = refreshes[id] ?: RefreshControl.Idle,
    )
}

/**
 * Which sources' film and series totals are worth asking for.
 *
 * A source that never finished an ingestion has nothing to count and would answer
 * `409`; one whose totals were read for the synchronisation it still shows is not
 * asked again. A **new** `last_synced_at` is a refresh that succeeded, and that is
 * exactly when the numbers on screen have to move (US-024).
 *
 * @param countedAt the `last_synced_at` each source had when its totals were read.
 */
internal fun sourcesToCount(
    sources: List<Source>,
    countedAt: Map<String, OffsetDateTime>,
): List<Source> = sources.filter { source ->
    val synced = source.lastSyncedAt
    synced != null && countedAt[source.id.toString()] != synced
}

/**
 * How often the list is read again while a synchronisation is on display.
 *
 * A little faster than the notice over a grid: here the refresh is the thing
 * somebody is watching. Polled from the composition, so it stops with the screen.
 */
internal const val SOURCE_LIST_POLL_MILLIS = 2_000L
