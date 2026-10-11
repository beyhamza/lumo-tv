package tv.lumo.android.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * A list read before anything was synchronised: one empty page, and nothing
 * that would ever invalidate it on its own — which is what Room's paging
 * source amounts to when the write lands during its first load
 * (BUG-R020-16-02). What invalidates it, the test checks.
 */
internal class EmptyPagingSource<Value : Any> : PagingSource<Int, Value>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Value> =
        LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

    override fun getRefreshKey(state: PagingState<Int, Value>): Int? = null
}
