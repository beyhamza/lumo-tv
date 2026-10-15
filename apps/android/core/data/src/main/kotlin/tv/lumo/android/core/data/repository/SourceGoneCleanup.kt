package tv.lumo.android.core.data.repository

import javax.inject.Inject
import tv.lumo.android.core.database.dao.CatalogueSyncDao

/**
 * What the device does with its own copy of a source once the source is known to
 * be gone (US-024, S11-05).
 *
 * <h2>One step, whoever noticed</h2>
 *
 * A source disappears from an account in several ways seen from here: this
 * device deleted it ([SourceRemover]), a call answered `404 SOURCE_NOT_FOUND`
 * (a player, a catalogue, the source screens), or a **successful** `GET /sources`
 * no longer lists it because another device deleted it. Each used to have its own
 * idea of what to clean, and only the first cleaned anything: the others left the
 * catalogue, the films, the series and the guide of a deleted source in Room, and
 * favourites and recent channels the cascade had already removed server-side.
 *
 * [ActiveSourceRepository] is the one place every one of those paths ends, so it
 * is the one that calls this — never a network error, which proves nothing.
 *
 * <h2>Not the `DELETE`</h2>
 *
 * Only the local side. The request is [SourceRemover]'s, and the other paths are
 * reactions to a deletion that has already happened.
 */
internal interface SourceGoneCleanup {

    /**
     * Sources this device holds a synchronised catalogue for.
     *
     * The witness of "a source this device knew" that survives a restart: a source
     * deleted elsewhere while the app was closed is absent from the list the
     * repository resolves at launch, and its cache is still here.
     */
    suspend fun cachedSources(): Set<String>

    /**
     * Drops the caches of [sourceIds] — channels, films, series, the guide — and
     * re-reads the account's favourites and recent channels once.
     *
     * The re-reads are best-effort: every screen filters those lists by the active
     * source, so a failed one only leaves rows to be replaced at the next read.
     */
    suspend fun purge(sourceIds: Collection<String>)

    /** Cleans nothing. For tests that are about something else. */
    object None : SourceGoneCleanup {
        override suspend fun cachedSources(): Set<String> = emptySet()
        override suspend fun purge(sourceIds: Collection<String>) = Unit
    }
}

internal class DefaultSourceGoneCleanup @Inject constructor(
    private val catalogue: CatalogueRepository,
    private val vod: VodRepository,
    private val series: SeriesRepository,
    private val epg: EpgRepository,
    private val favorites: FavoriteRepository,
    private val recents: RecentChannelRepository,
    private val syncs: CatalogueSyncDao,
) : SourceGoneCleanup {

    override suspend fun cachedSources(): Set<String> = syncs.sourceIds().toSet()

    override suspend fun purge(sourceIds: Collection<String>) {
        if (sourceIds.isEmpty()) return
        sourceIds.forEach { sourceId ->
            catalogue.forget(sourceId)
            vod.forget(sourceId)
            series.forget(sourceId)
            // The guide too (S9-03): programmes of a deleted source would sit in
            // the cache until the daily purge, joined to nothing.
            epg.forget(sourceId)
        }
        // Favourites, recent channels and progress went with the server's
        // cascade. The two lists cached here are the account's, replaced whole.
        favorites.refresh()
        recents.refresh()
    }
}
