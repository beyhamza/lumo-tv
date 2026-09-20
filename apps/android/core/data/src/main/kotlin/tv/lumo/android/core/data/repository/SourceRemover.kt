package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * Deleting a source, and everything the device has to do about it (US-024).
 *
 * <h2>Why it is not a line in a view model</h2>
 *
 * The order matters and has a wrong version. The server deletes first, because
 * **a failed deletion must change nothing locally**: dropping the cache and then
 * learning the request never left would leave a source that still exists with an
 * empty catalogue. Only then is the device's copy dropped — channels, films,
 * series, each from its own repository — and only then does
 * [ActiveSourceRepository] re-decide what is browsed: the one source left, a
 * question when several are, the add flow when none is.
 *
 * Here rather than in `feature:source` so the sequence is held by a test that
 * needs no screen, and so that a second caller cannot write it in another order.
 *
 * <h2>`404` is a success</h2>
 *
 * `SOURCE_NOT_FOUND` on a delete means another device got there first. The
 * source is gone, which is what was asked for, and the same cleaning applies —
 * it is one of the two proofs of deletion the product accepts
 * (c4-previous-catalogue.md §P6).
 *
 * <h2>What the cascade took with it</h2>
 *
 * Favourites, recently watched channels and playback progress of the source are
 * deleted server-side by cascade. The favourites and recent lists cached here
 * are the account's, replaced whole at each refresh, so they are re-read —
 * best-effort: every screen already filters them by the active source, so a
 * failed re-read shows nothing wrong, it only leaves rows to be replaced later.
 */
@Singleton
class SourceRemover internal constructor(
    private val delete: suspend (sourceId: String) -> LumoResult<Unit>,
    private val caches: List<suspend (sourceId: String) -> Unit>,
    private val accountLists: List<suspend () -> Unit>,
    private val activeSource: ActiveSourceRepository,
) {

    @Inject
    constructor(
        sources: SourceRepository,
        catalogue: CatalogueRepository,
        vod: VodRepository,
        series: SeriesRepository,
        favorites: FavoriteRepository,
        recents: RecentChannelRepository,
        activeSource: ActiveSourceRepository,
    ) : this(
        delete = { sourceId -> sources.delete(sourceId) },
        caches = listOf(
            { sourceId -> catalogue.forget(sourceId) },
            { sourceId -> vod.forget(sourceId) },
            { sourceId -> series.forget(sourceId) },
        ),
        accountLists = listOf(
            { favorites.refresh() },
            { recents.refresh() },
        ),
        activeSource = activeSource,
    )

    /**
     * @return the server's refusal when there was one — and then nothing at all
     * has changed on this device.
     */
    suspend fun remove(sourceId: String): LumoResult<Unit> {
        val result = delete(sourceId)
        if (result is LumoResult.Failure && !result.error.provesDeletion()) return result

        caches.forEach { forget -> forget(sourceId) }
        // Not `refresh()`: the deletion is proven, and a list that still carries
        // the source a moment later — or cannot be fetched at all — must not be
        // able to bring it back. `onSourceGone` filters it out either way.
        activeSource.onSourceGone(sourceId)
        accountLists.forEach { reload -> reload() }

        return LumoResult.Success(Unit)
    }

    private fun LumoError.provesDeletion(): Boolean =
        this is LumoError.Api && code == ErrorCode.SOURCE_NOT_FOUND
}
