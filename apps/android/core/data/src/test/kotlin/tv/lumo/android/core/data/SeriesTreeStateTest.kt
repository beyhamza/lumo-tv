package tv.lumo.android.core.data

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.model.SeriesTree
import tv.lumo.android.core.data.repository.SeriesRepository
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.EpisodeEntity
import tv.lumo.android.core.database.model.SeasonEntity
import tv.lumo.android.core.database.model.SeriesEntity
import tv.lumo.android.core.database.paging.SeriesPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * The three states of a series tree (US-15, S6-04).
 *
 * <h2>Why this is the test worth writing for this task</h2>
 *
 * Everything else in `SeriesRepository` is `VodRepository` again — the pagination
 * walk, the whole-or-nothing write, the origin bookkeeping — and those are proved
 * next door. What is genuinely new is that a detail screen has **three** answers
 * where a film had two, and the three look alike:
 *
 * - a panel that genuinely lists no seasons;
 * - a tree on its way;
 * - a provider that did not answer.
 *
 * Nothing crashes when two of them are collapsed. What happens is an empty screen
 * that reads as a series with no episodes, or a spinner that never stops on a
 * series already held — both of them found by a user, never by a build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesTreeStateTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    private val seriesDao = FakeSeriesDao()
    private val categoryDao = FakeSeriesCategoryDao()

    private val sourceId = UUID.randomUUID().toString()
    private val seriesId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(CatalogApi::class.java)

        seriesDao.stored += seriesRow(treeFetchedAt = null)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `a series nobody has opened is idle, not loading and not empty`() = runTest {
        // The first frame of a detail screen. Drawing a spinner here would be
        // claiming a request that has not been made; drawing an empty season list
        // would be claiming an answer nobody asked for.
        assertThat(repository().tree(seriesId).first()).isEqualTo(SeriesTree.Idle)
    }

    @Test
    fun `a tree that arrives is loaded, seasons and episodes back together`() = runTest {
        server.enqueue(json(detail(seasons = 2)))

        val repository = repository()
        repository.loadTree(seriesId)

        val tree = repository.tree(seriesId).first()
        assertThat(tree).isInstanceOf(SeriesTree.Loaded::class.java)

        val loaded = tree as SeriesTree.Loaded
        assertThat(loaded.seasons).hasSize(2)
        assertThat(loaded.seasons[0].episodes.map { it.episodeNumber }).containsExactly(1, 2)
        assertThat(loaded.stale).isFalse()
    }

    @Test
    fun `a panel that lists no season is loaded and empty, not unavailable`() = runTest {
        server.enqueue(json(detail(seasons = 0)))

        val repository = repository()
        repository.loadTree(seriesId)

        // Rare and real: some panels list a series and answer nothing for it. The
        // screen shows a series with no episodes, which is the truth — not a
        // failure, and not a spinner.
        val tree = repository.tree(seriesId).first()
        assertThat(tree).isEqualTo(SeriesTree.Loaded(emptyList(), stale = false))
    }

    @Test
    fun `nothing cached and a provider that fails is unavailable, never an empty tree`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"code":"SOURCE_UNREACHABLE"}"""),
        )

        val repository = repository()
        val result = repository.loadTree(seriesId)

        assertThat(result).isInstanceOf(LumoResult.Failure::class.java)
        // The distinction that decides what a person is told. An empty tree here
        // would say their series has no episodes, which is a different and far more
        // alarming thing than "your provider did not answer".
        assertThat(repository.tree(seriesId).first()).isEqualTo(SeriesTree.Unavailable)
    }

    @Test
    fun `a cached tree survives a failed refresh, and is still shown`() = runTest {
        seriesDao.stored[0] = seriesRow(treeFetchedAt = 1_000L)
        seriesDao.seasons += seasonRow(1)
        seriesDao.episodes += episodeRow(1, 1)

        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"code":"SOURCE_UNREACHABLE"}"""),
        )

        val repository = repository()
        repository.loadTree(seriesId)

        // What is held is old, not wrong. Replacing it with `Unavailable` because a
        // refresh failed would take away episodes somebody can still watch.
        val tree = repository.tree(seriesId).first()
        assertThat(tree).isInstanceOf(SeriesTree.Loaded::class.java)
        assertThat((tree as SeriesTree.Loaded).seasons).hasSize(1)
    }

    @Test
    fun `a season the panel lists with no episodes is kept`() = runTest {
        seriesDao.stored[0] = seriesRow(treeFetchedAt = 1_000L)
        seriesDao.seasons += seasonRow(1)
        seriesDao.seasons += seasonRow(2)
        seriesDao.episodes += episodeRow(1, 1)

        val tree = repository().tree(seriesId).first() as SeriesTree.Loaded

        // Grouped from the season list rather than from the episodes, precisely so
        // this one survives. Building the tree from episodes would make season 2
        // disappear, which is hiding something the panel said.
        assertThat(tree.seasons.map { it.seasonNumber }).containsExactly(1, 2)
        assertThat(tree.seasons[1].episodes).isEmpty()
    }

    @Test
    fun `a listing never claims a tree it has not got`() = runTest {
        server.enqueue(json("""{"items":[]}"""))
        server.enqueue(json(seriesPage()))

        val repository = repository()
        repository.refresh(sourceId)

        // The stamp is written by `replaceTree` and by nothing else. A refresh that
        // stamped rows would leave every series looking cached, and every detail
        // screen showing an empty tree instead of fetching one.
        assertThat(seriesDao.stored.all { it.treeFetchedAt == null }).isTrue()
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = SeriesRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        categoryDao = categoryDao,
        seriesDao = seriesDao,
        pager = SeriesPager(seriesDao),
        io = UnconfinedTestDispatcher(),
    )

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun seriesJson() = """
        {"id":"$seriesId","source_id":"$sourceId","name":"Les Falaises",
         "position":0,"is_adult":false,"plot":"Un synopsis."}
    """.trimIndent()

    private fun seriesPage() = """
        {"items":[${seriesJson()}],"page":0,"size":200,"total_elements":1,"total_pages":1}
    """.trimIndent()

    private fun detail(seasons: Int): String {
        val tree = (1..seasons).joinToString(",") { number ->
            val episodes = (1..2).joinToString(",") { episode ->
                """
                {"id":"${UUID.randomUUID()}","series_id":"$seriesId","source_id":"$sourceId",
                 "season_number":$number,"episode_number":$episode}
                """.trimIndent()
            }
            """{"season_number":$number,"episodes":[$episodes]}"""
        }
        return """{"series":${seriesJson()},"seasons":[$tree]}"""
    }

    private fun seriesRow(treeFetchedAt: Long?) = SeriesEntity(
        id = seriesId,
        sourceId = sourceId,
        categoryId = null,
        externalId = "s:1",
        name = "Les Falaises",
        posterUrl = null,
        year = null,
        episodeRunTime = null,
        rating = null,
        plot = null,
        treeFetchedAt = treeFetchedAt,
        position = 0,
        isAdult = false,
    )

    private fun seasonRow(number: Int) = SeasonEntity(
        id = "$seriesId:$number",
        seriesId = seriesId,
        seasonNumber = number,
        episodeCount = null,
        posterUrl = null,
    )

    private fun episodeRow(season: Int, episode: Int) = EpisodeEntity(
        id = UUID.randomUUID().toString(),
        seriesId = seriesId,
        seasonId = "$seriesId:$season",
        sourceId = sourceId,
        externalId = "e:$season:$episode",
        seasonNumber = season,
        episodeNumber = episode,
        name = null,
        durationSeconds = null,
        plot = null,
    )
}

/**
 * The three series tables, in three lists.
 *
 * `replaceTree` and `replaceForSource` are inherited from the interface rather than
 * reimplemented: their ordering is part of what these tests check, and a fake that
 * reimplemented them could pass while the real ones were wrong.
 */
private class FakeSeriesDao : SeriesDao {

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

private class FakeSeriesCategoryDao : CategoryDao {

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
