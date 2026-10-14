package tv.lumo.android.core.data.internal

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tv.lumo.android.core.auth.SessionEndCleaner
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.database.DatabaseEraser

/**
 * Everything this module keeps on disk for an account, removed when its session
 * ends (`BUG-R020-01-01`, release lock `R020-01`).
 *
 * The recette of 10 October 2026 signed account A out of a television and
 * activated B on it: B saw nothing of A, but A's favourites, history and channel
 * names were still in `lumo-catalogue.db`, and A's catalogue was still there
 * after B arrived. Hidden is not gone.
 *
 * - **The whole Room database.** Every table is account data: the catalogue of
 *   the account's sources, its favourites, groups, recent channels, the guide. A
 *   table-by-table purge would be one more list to forget to update; the next
 *   account syncs its own catalogue anyway.
 * - **Both preference files.** The active source is keyed by account and the
 *   Direct view by source; neither means anything to the next account.
 *
 * The session itself is `core:auth`'s, and it is already gone when this runs.
 */
internal class AccountDataCleaner @Inject constructor(
    private val database: DatabaseEraser,
    private val activeSources: ActiveSourceStore,
    private val directViews: DirectViewStore,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) : SessionEndCleaner {

    override suspend fun onSessionEnded() {
        // Blocking, and refused on the main thread by Room: hence the dispatcher.
        withContext(io) { database.eraseAll() }
        activeSources.clearAll()
        directViews.clearAll()
    }
}
