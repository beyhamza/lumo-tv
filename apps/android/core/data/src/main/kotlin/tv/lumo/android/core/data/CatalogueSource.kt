package tv.lumo.android.core.data

import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
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

    /**
     * No ingestion of the active source has **ever** succeeded (`last_synced_at`
     * is null) and its status is not `READY`: a first import that is running, or
     * one that failed.
     *
     * Until C4 this was "not ready" and covered every status but `READY`, which
     * hid a catalogue the server still held and Room still cached each time a
     * source refreshed. What is left here is the one situation where the server
     * has nothing to list (`409 SOURCE_NOT_READY`, c4-previous-catalogue.md §P1).
     * Whether the *device* still holds rows is the screen's question — see
     * [face].
     *
     * @param failed `ERROR` rather than `PENDING`/`SYNCING`. Two different
     * sentences: one says wait, the other says go and fix it.
     */
    data class FirstImport(
        val sourceId: String,
        val isPlaylist: Boolean,
        val failed: Boolean,
    ) : CatalogueSource

    /**
     * There is a catalogue to browse: the source is `READY`, **or** it is
     * refreshing or in error with a previous ingestion behind it, or nothing is
     * known about it but its identifier.
     *
     * The status is deliberately absent. It changes with every step of a
     * synchronisation, a grid restarts its pager when this value changes, and
     * what a grid says about a refresh is a notice over it
     * ([SourceNotice]) — never a reason to rebuild it.
     *
     * @param isPlaylist whether the source is a playlist rather than a panel,
     * which only the series screen reads — see `SeriesState.isPlaylist`. False
     * when the source itself is unknown, which is the cautious of the two.
     * @param unreached the identifier is the device's own and the server could
     * not be reached to say anything about the source (US-024, "Indisponibilité
     * et hors ligne"). It changes exactly once per outage — when the list comes
     * back — so the pager it restarts is one that was about to refresh anyway.
     * What it decides is in [face]: a cached catalogue is shown under a notice, an
     * empty cache is an explanation and not an empty grid.
     */
    data class Ready(
        val sourceId: String,
        val isPlaylist: Boolean,
        val unreached: Boolean = false,
    ) : CatalogueSource

    /**
     * The list of sources could not be fetched and this device holds no choice.
     *
     * Kept apart from [NoSource] since S8-06, for the reason `ActiveSourceState`
     * keeps `Unavailable` apart from `None`: one is the server saying the
     * account has no source, the other is an absence of facts. A grid that read
     * the second as the first told somebody on a train that their subscription
     * was gone (US-024: an outage is never a proof of deletion).
     */
    data object Unreachable : CatalogueSource
}

/** The identifier a grid reads its cache with, or null when there is nothing to read. */
val CatalogueSource.sourceId: String?
    get() = when (this) {
        is CatalogueSource.FirstImport -> sourceId
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
 * <h2>[ActiveSourceState.Unavailable] is an outage, not an absence of source</h2>
 *
 * Nothing is known and nothing is cached under any identifier this device holds,
 * so there is no grid to draw — but "add a source" would be the wrong answer to
 * a network that blinked. It reads as [CatalogueSource.Unreachable], which a
 * grid words as an outage with *try again* and *change source* (US-024,
 * "Indisponibilité et hors ligne", closed in S8-06). Until then it inherited
 * [CatalogueSource.NoSource], which only the home screen told apart.
 *
 * <h2>The status no longer decides whether there is a grid (C4)</h2>
 *
 * Every status but `READY` used to become "not ready", which hid a catalogue
 * the device had cached and the server still listed. A source is now browsable
 * as soon as one ingestion has succeeded — `last_synced_at`, which no failure
 * and no refresh ever clears — and only a source that never had one is a
 * [CatalogueSource.FirstImport].
 */
fun ActiveSourceState.asCatalogueSource(): CatalogueSource = when (this) {
    ActiveSourceState.Loading -> CatalogueSource.Loading
    ActiveSourceState.None -> CatalogueSource.NoSource
    ActiveSourceState.Unavailable -> CatalogueSource.Unreachable
    is ActiveSourceState.NeedsChoice -> CatalogueSource.NeedsChoice
    is ActiveSourceState.Selected -> {
        val playlist = source != null && source.kind != SourceKind.XTREAM
        when {
            source != null && source.status != SourceStatus.READY && source.lastSyncedAt == null ->
                CatalogueSource.FirstImport(
                    sourceId = sourceId,
                    isPlaylist = playlist,
                    failed = source.status == SourceStatus.ERROR,
                )

            else -> CatalogueSource.Ready(
                sourceId = sourceId,
                isPlaylist = playlist,
                unreached = source == null,
            )
        }
    }
}

/**
 * Which of its faces a catalogue grid shows.
 *
 * One enum for the three grids, so that "is there something to browse" has one
 * answer. Each feature maps it onto its own step type, one to one.
 */
enum class CatalogueFace {
    Loading,
    NoSource,
    NeedsChoice,

    /** A first import is running and the device holds nothing of this source. */
    Importing,

    /** The first import failed and the device holds nothing of this source. */
    ImportFailed,

    /**
     * The server could not be reached and there is nothing to draw: no choice
     * recorded on this device, or a choice whose catalogue was never cached.
     * The grid explains, offers to try again and to change source — and says
     * that nothing was removed (US-024).
     */
    Unreachable,

    Browsing,
}

/**
 * The visibility rule of lot C4, as a pure function of the two things it reads.
 *
 * **A catalogue that exists is shown, in every status.** It exists when the
 * server says an ingestion succeeded once ([CatalogueSource.Ready] — which
 * includes `SYNCING`, `PENDING` and `ERROR` with a `last_synced_at`) or when the
 * device's own cache holds rows for the source. Only when neither is true does a
 * grid show the first-import state, and then it says which of the two it is —
 * importing or failed — instead of the single "not ready" both used to share.
 *
 * **An outage shows what the device holds, and nothing else** (US-024,
 * "Indisponibilité et hors ligne"). A choice remembered offline draws its cached
 * catalogue under a notice that it may be out of date; the same choice with an
 * empty cache has no list to show and says so — never "no films", which would
 * be a statement about the source made from a request that never reached it.
 *
 * @param cachedItems how many rows of *this* catalogue Room holds for the
 * source. Zero and unknown are the same here: nothing to draw.
 */
fun CatalogueSource.face(cachedItems: Int): CatalogueFace = when (this) {
    CatalogueSource.Loading -> CatalogueFace.Loading
    CatalogueSource.NoSource -> CatalogueFace.NoSource
    CatalogueSource.NeedsChoice -> CatalogueFace.NeedsChoice
    CatalogueSource.Unreachable -> CatalogueFace.Unreachable
    is CatalogueSource.Ready ->
        if (unreached && cachedItems == 0) CatalogueFace.Unreachable else CatalogueFace.Browsing
    is CatalogueSource.FirstImport -> when {
        cachedItems > 0 -> CatalogueFace.Browsing
        failed -> CatalogueFace.ImportFailed
        else -> CatalogueFace.Importing
    }
}

/** Whether the source is a playlist, for the one screen that words an absence with it. */
val CatalogueSource.isPlaylist: Boolean
    get() = when (this) {
        is CatalogueSource.Ready -> isPlaylist
        is CatalogueSource.FirstImport -> isPlaylist
        else -> false
    }

/**
 * What a grid with no card in it is saying.
 *
 * US-024, "partially available": a section that **failed to load** says so and
 * is never presented as empty. Until this existed an empty film grid whose
 * refresh had just failed read "this source has no films" — a statement about the
 * source made from a request that never reached it.
 */
enum class EmptyGrid {
    /** There are cards. Nothing to say. */
    None,

    /** Being fetched. A spinner's job, not a sentence's. */
    Loading,

    /** The fetch answered and the source carries none. A reply. */
    Empty,

    /** The fetch failed and nothing was cached. Worth retrying; not a fact about the source. */
    Unavailable,
}

fun emptyGridOf(itemCount: Int, refreshing: Boolean, refreshFailed: Boolean): EmptyGrid = when {
    itemCount > 0 -> EmptyGrid.None
    refreshing -> EmptyGrid.Loading
    refreshFailed -> EmptyGrid.Unavailable
    else -> EmptyGrid.Empty
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

/**
 * Every favourite of the source being browsed, **each channel once** (US-020).
 *
 * What the home screen's "Favourites" rail and the television's "My library" grid
 * both draw. One function, in `core:data`, because the two must never disagree
 * about where a channel sits — and because the rule has a right and a wrong answer,
 * which makes it the part worth holding in a test.
 *
 * <h2>The rule, as the story states it</h2>
 *
 * Walk the groups in their order, then the channels in their order inside each
 * group; **the first occurrence of a channel decides its place**. The contract
 * allows one channel in several groups, and this removes none of those
 * memberships — it only decides where the channel is drawn when the groups are
 * shown together. No separate ranking is kept for the home screen.
 *
 * **Identity is the channel's id, not its name.** Two subscriptions, or two
 * categories of one, carry channels with the same name often enough that a name
 * would merge things the user filed separately.
 *
 * <h2>Sorted here, on purpose</h2>
 *
 * The DAO already returns both lists in order, and this sorts them again. It is
 * not a second opinion about order — `position` is the only one there is — but a
 * pure function that silently depended on its caller having sorted first would be
 * right in production and wrong in the first test that built a list by hand.
 *
 * A favourite whose group is not in [groups] is kept, after the others. That is a
 * cache read between two writes — the favourites landed, their group has not yet —
 * and dropping the channel for a frame would make it blink out of a rail.
 */
fun aggregatedFavorites(
    groups: List<FavoriteGroup>,
    favorites: List<FavoriteChannel>,
    sourceId: String?,
): List<FavoriteChannel> {
    val mine = favorites.ofSource(sourceId)
    if (mine.isEmpty()) return emptyList()

    val byGroup = mine.groupBy { it.groupId }
    val known = groups.sortedBy { it.position }.map { it.id }
    val orphaned = byGroup.keys - known.toSet()

    return (known + orphaned)
        .flatMap { groupId -> byGroup[groupId].orEmpty().sortedBy { it.position } }
        .distinctBy { it.channel.id }
}
