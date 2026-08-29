package tv.lumo.android.core.database.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.dao.VodDao
import tv.lumo.android.core.database.model.VodItemEntity

/**
 * Paging 3 for films, configured once for the whole product.
 *
 * <h2>Why the page is smaller than [CataloguePager]'s, and not by taste</h2>
 *
 * A channel row is a line of text and a 32 dp logo. A film card is a poster, and
 * a poster held in memory at grid size is roughly fifty times the bitmap of a
 * logo. Paging's window is what decides how many of those exist at once, so the
 * number that made a channel list smooth is the number that makes a film grid run
 * out of heap on a TV box with a gigabyte to work with.
 *
 * Smaller pages, fetched more often, is the right trade here and the wrong one
 * for channels — which is why this is a second pager rather than a parameter on
 * the first. [prefetchDistance] still covers a screen's worth of scroll, so the
 * D-pad does not reach a loading gap; what shrinks is how much is kept behind it.
 */
class VodPager @Inject constructor(
    private val vodDao: VodDao,
) {

    fun filmsInCategory(sourceId: String, categoryId: String): Flow<PagingData<VodItemEntity>> =
        pager { vodDao.pagedByCategory(sourceId, categoryId) }

    fun filmsInSource(sourceId: String): Flow<PagingData<VodItemEntity>> =
        pager { vodDao.pagedBySource(sourceId) }

    fun search(sourceId: String, query: String): Flow<PagingData<VodItemEntity>> =
        pager { vodDao.pagedBySearch(sourceId, query) }

    private fun pager(
        source: () -> PagingSource<Int, VodItemEntity>,
    ): Flow<PagingData<VodItemEntity>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            // Two pages to start: enough to fill a 1080p poster grid and the
            // screen below it, which is what the first D-pad flick crosses.
            initialLoadSize = PAGE_SIZE * 2,
            prefetchDistance = PAGE_SIZE,
            // The ceiling that matters. Without it Paging keeps every page it has
            // loaded, and a user who scrolls through two thousand films has two
            // thousand posters' worth of decoded bitmap behind them. Coil's memory
            // cache would not save that: the images are still referenced by the
            // items Paging is holding.
            maxSize = PAGE_SIZE * 6,
            // Placeholders are what give the scrollbar a real size on a
            // thirty-thousand-film catalogue instead of one that grows as you
            // scroll — and, with `maxSize` set, what keeps a dropped page from
            // shifting everything below it.
            enablePlaceholders = true,
        ),
        pagingSourceFactory = source,
    ).flow

    private companion object {
        /**
         * Sized against the poster, not the row.
         *
         * A 1080p grid shows around eighteen cards; a phone shows nine. Thirty
         * covers either with a page in hand, and `maxSize` above keeps at most
         * six of these windows alive.
         */
        const val PAGE_SIZE = 30
    }
}
