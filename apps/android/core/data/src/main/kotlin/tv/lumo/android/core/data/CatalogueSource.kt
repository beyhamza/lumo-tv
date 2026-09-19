package tv.lumo.android.core.data

import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * What a catalogue screen makes of [ActiveSourceState] (US-018).
 *
 * Channels, films and series ask the same question of the active source — is
 * there one, and can it be browsed — and used to answer it three times over, each
 * with its own copy of "the first source of the list". Said once here, the three
 * grids cannot come to disagree about which source they show, nor about what a
 * source that is still importing looks like.
 *
 * <h2>Narrower than the state it comes from, on purpose</h2>
 *
 * A grid restarts its pager when this value changes. [ActiveSourceState.Selected]
 * carries the whole `Source` and the whole list, and those change for reasons a
 * grid does not care about — a sync date, a counter, a sibling source renamed.
 * Reducing to an identifier and two facts is what lets `distinctUntilChanged`
 * keep a poster wall from rebuilding because another source finished importing.
 */
sealed interface CatalogueSource {

    data object Loading : CatalogueSource

    /** Nothing to browse, and the way forward is registering a source. */
    data object NoSource : CatalogueSource

    /** Several sources and no choice yet. The shell is asking; the grid waits. */
    data object NeedsChoice : CatalogueSource

    /** The active source has not finished importing, or failed to. */
    data class NotReady(val sourceId: String) : CatalogueSource

    /**
     * @param isPlaylist whether the source is a playlist rather than a panel,
     * which only the series screen reads — see `SeriesState.isPlaylist`. False
     * when the source itself is unknown, which is the cautious of the two.
     */
    data class Ready(val sourceId: String, val isPlaylist: Boolean) : CatalogueSource
}

/** The identifier a grid reads its cache with, or null when there is nothing to read. */
val CatalogueSource.sourceId: String?
    get() = when (this) {
        is CatalogueSource.NotReady -> sourceId
        is CatalogueSource.Ready -> sourceId
        else -> null
    }

/**
 * <h2>A choice without a list is browsable</h2>
 *
 * [ActiveSourceState.Selected] with no `Source` is a device that knows *which*
 * source it browses and could not reach the server to learn anything else. The
 * catalogue is cache-first, so that identifier is enough to draw the grid, and
 * the screen's own origin indicator already says the list may be old (US-024,
 * "Indisponibilité et hors ligne"). Waiting for a status that cannot arrive would
 * turn "offline" into "no catalogue".
 *
 * <h2>[ActiveSourceState.Unavailable] reads as no source, and that is inherited</h2>
 *
 * Nothing is known and nothing is cached under any identifier this device holds,
 * so there is no grid to draw. It is what these screens already showed when the
 * list could not be fetched; telling the two apart on screen belongs to the
 * offline states of S8-05, not here.
 */
fun ActiveSourceState.asCatalogueSource(): CatalogueSource = when (this) {
    ActiveSourceState.Loading -> CatalogueSource.Loading
    ActiveSourceState.None, ActiveSourceState.Unavailable -> CatalogueSource.NoSource
    is ActiveSourceState.NeedsChoice -> CatalogueSource.NeedsChoice
    is ActiveSourceState.Selected -> when {
        source != null && source.status != SourceStatus.READY -> CatalogueSource.NotReady(sourceId)
        else -> CatalogueSource.Ready(
            sourceId = sourceId,
            isPlaylist = source != null && source.kind != SourceKind.XTREAM,
        )
    }
}

/**
 * The favourites that belong to the source being browsed (US-018).
 *
 * Favourites are the account's and a group may mix two subscriptions; what is
 * *shown* is the active source's share of them. Filtered on the device because
 * the API has no such filter, and only ever filtered: coming back to a source
 * finds its favourites exactly as they were.
 *
 * A null source shows nothing rather than everything — with no active source
 * there is no catalogue on screen for a favourite to belong to.
 */
fun List<FavoriteChannel>.ofSource(sourceId: String?): List<FavoriteChannel> =
    if (sourceId == null) emptyList() else filter { it.channel.sourceId == sourceId }

/** The same rule for a plain list of channels — the recently watched ones. */
fun List<Channel>.channelsOfSource(sourceId: String?): List<Channel> =
    if (sourceId == null) emptyList() else filter { it.sourceId == sourceId }
