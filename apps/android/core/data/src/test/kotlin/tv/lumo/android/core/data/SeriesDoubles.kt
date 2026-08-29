package tv.lumo.android.core.data

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity

/**
 * The series test doubles, shared by the tests that need a tree in a cache.
 *
 * Out of `SeriesTreeStateTest` and into their own file the moment a second test
 * needed them (S6-08), rather than copied: a fake that drifts from the one another
 * test uses is two different databases pretending to be one.
 */
/**
 * The three series tables, in three lists.
 *
 * `replaceTree` and `replaceForSource` are inherited from the interface rather than
 * reimplemented: their ordering is part of what these tests check, and a fake that
 * reimplemented them could pass while the real ones were wrong.
 */
internal class FakeSeriesDao : SeriesDao {

    val stored = mutableListOf<SeriesEntity>()
    val seasons = mutableListOf<SeasonEntity>()
    val episodes = mutableListOf<EpisodeEntity>()

    private val revision = MutableStateFlow(0)

    override suspend fun upsert(series: List<SeriesEntity>) {
        series.forEach { row ->
            stored.removeAll { it.id == row.id }
            stored += row
        }
        revision.value++
    }

    override suspend fun deleteBySource(sourceId: String) {
        stored.removeAll { it.sourceId == sourceId }
        revision.value++
    }

    override suspend fun countForSource(sourceId: String): Int =
        stored.count { it.sourceId == sourceId }

    override fun observe(id: String): Flow<SeriesEntity?> =
        revision.map { stored.firstOrNull { row -> row.id == id } }

    override suspend fun byIds(ids: List<String>): List<SeriesEntity> =
        stored.filter { it.id in ids }

    override fun observeSeasons(seriesId: String): Flow<List<SeasonEntity>> =
        revision.map { seasons.filter { it.seriesId == seriesId }.sortedBy { it.seasonNumber } }

    override fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>> =
        revision.map {
            episodes.filter { it.seriesId == seriesId }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        }

    override suspend fun seasonsOf(seriesId: String): List<SeasonEntity> =
        seasons.filter { it.seriesId == seriesId }.sortedBy { it.seasonNumber }

    override suspend fun episodesOf(seriesId: String): List<EpisodeEntity> =
        episodes.filter { it.seriesId == seriesId }
            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    override suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity> =
        episodes.filter { it.id in ids }

    override suspend fun deleteSeasons(seriesId: String) {
        // The cascade, by hand: SQLite does it through the foreign key.
        val gone = seasons.filter { it.seriesId == seriesId }.map { it.id }.toSet()
        seasons.removeAll { it.seriesId == seriesId }
        episodes.removeAll { it.seasonId in gone }
        revision.value++
    }

    override suspend fun upsertSeasons(seasons: List<SeasonEntity>) {
        seasons.forEach { row ->
            this.seasons.removeAll { it.id == row.id }
            this.seasons += row
        }
        revision.value++
    }

    override suspend fun upsertEpisodes(episodes: List<EpisodeEntity>) {
        episodes.forEach { row ->
            this.episodes.removeAll { it.id == row.id }
            this.episodes += row
        }
        revision.value++
    }

    override suspend fun stampTree(seriesId: String, fetchedAt: Long) {
        val index = stored.indexOfFirst { it.id == seriesId }
        if (index >= 0) stored[index] = stored[index].copy(treeFetchedAt = fetchedAt)
        revision.value++
    }

    override suspend fun updatePlot(seriesId: String, plot: String?) {
        val index = stored.indexOfFirst { it.id == seriesId }
        if (index >= 0) stored[index] = stored[index].copy(plot = plot)
        revision.value++
    }

    override fun pagedByCategory(
        sourceId: String,
        categoryId: String,
    ): PagingSource<Int, SeriesEntity> = unreachable()

    override fun pagedBySource(sourceId: String): PagingSource<Int, SeriesEntity> = unreachable()

    override fun pagedBySearch(
        sourceId: String,
        query: String,
    ): PagingSource<Int, SeriesEntity> = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("Paging is Room's, and is not what these tests are about")
}

internal class FakeSeriesCategoryDao : CategoryDao {

    val stored = MutableStateFlow<List<CategoryEntity>>(emptyList())

    override fun observeBySource(
        sourceId: String,
        contentType: String,
    ): Flow<List<CategoryEntity>> = stored.map { all ->
        all.filter { it.sourceId == sourceId && it.contentType == contentType }
    }

    override suspend fun upsert(categories: List<CategoryEntity>) {
        stored.value = stored.value + categories
    }

    override suspend fun deleteBySource(sourceId: String) {
        stored.value = stored.value.filterNot { it.sourceId == sourceId }
    }

    override suspend fun deleteBySourceAndType(sourceId: String, contentType: String) {
        stored.value = stored.value
            .filterNot { it.sourceId == sourceId && it.contentType == contentType }
    }
}
