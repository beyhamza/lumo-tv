package tv.lumo.android.core.database.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.model.ChannelEntity

/**
 * Paging 3, configured once for the whole product.
 *
 * The page size is tuned for the television rather than the phone, and
 * deliberately so: a TV grid shows more rows at once and the D-pad crosses them
 * far faster than a thumb does, so a page size that feels generous on a phone
 * makes a channel grid stutter at the bottom edge under the remote. Reading the
 * same window twice on a phone costs nothing; running out of loaded items on a
 * TV is visible as a hitch (US-08, US-10).
 */
class CataloguePager @Inject constructor(
    private val channelDao: ChannelDao,
) {

    fun channelsInCategory(sourceId: String, categoryId: String): Flow<PagingData<ChannelEntity>> =
        pager { channelDao.pagedByCategory(sourceId, categoryId) }

    fun channelsInSource(sourceId: String): Flow<PagingData<ChannelEntity>> =
        pager { channelDao.pagedBySource(sourceId) }

    fun search(sourceId: String, query: String): Flow<PagingData<ChannelEntity>> =
        pager { channelDao.pagedBySearch(sourceId, query) }

    private fun pager(
        source: () -> androidx.paging.PagingSource<Int, ChannelEntity>,
    ): Flow<PagingData<ChannelEntity>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            // Start with enough rows to fill a 1080p grid plus a screen of
            // scroll, so the first D-pad flick does not hit a loading gap.
            initialLoadSize = PAGE_SIZE * 3,
            prefetchDistance = PAGE_SIZE,
            // Paging's placeholders are what let the scrollbar have a real size
            // on a 15 000-channel list instead of growing as you scroll.
            enablePlaceholders = true,
        ),
        pagingSourceFactory = source,
    ).flow

    private companion object {
        const val PAGE_SIZE = 60
    }
}
