package tv.lumo.android.core.database.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.model.SeriesEntity

/**
 * Paging 3 for series.
 *
 * [VodPager]'s configuration, taken as it stands and for the reason that pager
 * exists at all: a series card is a poster, so it costs what a film card costs,
 * and the memory ceiling that made a film grid survive a TV box is the same
 * ceiling here.
 *
 * A third pager rather than a parameter on the second, for the reason the second
 * is not a parameter on the first: each takes its own DAO, and a shared one would
 * take a table name from Kotlin.
 */
class SeriesPager @Inject constructor(
    private val seriesDao: SeriesDao,
) {

    fun seriesInCategory(sourceId: String, categoryId: String): Flow<PagingData<SeriesEntity>> =
        pager { seriesDao.pagedByCategory(sourceId, categoryId) }

    fun seriesInSource(sourceId: String): Flow<PagingData<SeriesEntity>> =
        pager { seriesDao.pagedBySource(sourceId) }

    fun search(sourceId: String, query: String): Flow<PagingData<SeriesEntity>> =
        pager { seriesDao.pagedBySearch(sourceId, query) }

    private fun pager(
        source: () -> PagingSource<Int, SeriesEntity>,
    ): Flow<PagingData<SeriesEntity>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE * 2,
            prefetchDistance = PAGE_SIZE,
            // The ceiling that matters, and it matters here for the reason it
            // matters on the films: without it Paging keeps every page loaded, and
            // a viewer who scrolls through a thousand series is holding a thousand
            // posters' worth of decoded bitmap.
            maxSize = PAGE_SIZE * 6,
            enablePlaceholders = true,
        ),
        pagingSourceFactory = source,
    ).flow

    private companion object {
        /** Sized against the poster, exactly as `VodPager`'s is. */
        const val PAGE_SIZE = 30
    }
}
