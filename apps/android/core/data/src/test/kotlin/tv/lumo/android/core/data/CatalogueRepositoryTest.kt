package tv.lumo.android.core.data

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.repository.CatalogueRepository
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.core.database.paging.CataloguePager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * Filling the offline catalogue, and saying where the answer came from (US-08).
 *
 * <h2>Why this is worth a test</h2>
 *
 * Two behaviours here are invisible until they are wrong on somebody's phone.
 *
 * The first is that a refresh walks the **whole** pagination. A cache holding the
 * first page only is not a cache — it is a screen's scroll position, and the
 * second flick on a train hits an empty list. Nothing crashes if the loop stops
 * early; the list is simply short, and short in a way that looks like a small
 * playlist.
 *
 * The second is the origin. Room has rows whether or not the last refresh
 * succeeded, so a screen cannot work out on its own whether it is showing
 * yesterday's catalogue — and a catalogue silently a week old is the failure a
 * user cannot diagnose. US-08 asks for it to be said, and this is what proves it
 * is said truthfully rather than always.
 *
 * The DAOs are fakes rather than a real database: what is under test is the walk
 * and the bookkeeping, and Room needs an Android runtime that the build
 * deliberately does not ask for (AGENTS.md §8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    private val channelDao = FakeChannelDao()
    private val categoryDao = FakeCategoryDao()

    private val sourceId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(CatalogApi::class.java)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `a refresh walks every page, not just the first`() = runTest {
        server.enqueue(json(categoryList()))
        server.enqueue(json(channelPage(page = 0, totalPages = 3, names = listOf("A", "B"))))
        server.enqueue(json(channelPage(page = 1, totalPages = 3, names = listOf("C", "D"))))
        server.enqueue(json(channelPage(page = 2, totalPages = 3, names = listOf("E"))))

        val result = repository().refresh(sourceId)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        // Five channels across three pages. A loop that stopped at the first
        // would leave two, and look like a small playlist rather than a bug.
        assertThat(channelDao.stored.map { it.name })
            .containsExactly("A", "B", "C", "D", "E")
            .inOrder()
    }

    @Test
    fun `the catalogue is replaced whole, never merged`() = runTest {
        channelDao.stored += entity("Gone since the last sync")

        server.enqueue(json(categoryList()))
        server.enqueue(json(channelPage(page = 0, totalPages = 1, names = listOf("Still here"))))

        repository().refresh(sourceId)

        // A re-synchronisation can drop half a catalogue. Merging would leave
        // channels the user's subscription no longer carries — worse than a stale
        // list, because it looks current.
        assertThat(channelDao.stored.map { it.name }).containsExactly("Still here")
    }

    @Test
    fun `a failure part-way writes nothing`() = runTest {
        channelDao.stored += entity("Yesterday")

        server.enqueue(json(categoryList()))
        server.enqueue(json(channelPage(page = 0, totalPages = 2, names = listOf("A"))))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val result = repository().refresh(sourceId)

        assertThat(result).isInstanceOf(LumoResult.Failure::class.java)
        // The half that arrived is discarded. A partially applied replacement is
        // a catalogue that is neither the old one nor the new one.
        assertThat(channelDao.stored.map { it.name }).containsExactly("Yesterday")
    }

    @Test
    fun `before any refresh, the answer is the cache and says so`() = runTest {
        categoryDao.stored.value = listOf(categoryEntity("Sport"))

        val cached = repository().categories(sourceId).first()

        // Rows that are already there came from an earlier run, by definition.
        // Saying "network" here would be the screen claiming a freshness nobody
        // checked.
        assertThat(cached.origin).isEqualTo(DataOrigin.Cache)
        assertThat(cached.value.map { it.name }).containsExactly("Sport")
    }

    @Test
    fun `a successful refresh makes the answer fresh`() = runTest {
        server.enqueue(json(categoryList()))
        server.enqueue(json(channelPage(page = 0, totalPages = 1, names = listOf("A"))))

        val repository = repository()
        repository.refresh(sourceId)

        assertThat(repository.categories(sourceId).first().origin).isEqualTo(DataOrigin.Network)
    }

    @Test
    fun `a failed refresh serves the cache, and carries the reason`() = runTest {
        categoryDao.stored.value = listOf(categoryEntity("Sport"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val repository = repository()
        repository.refresh(sourceId)

        val cached = repository.categories(sourceId).first()

        assertThat(cached.origin).isEqualTo(DataOrigin.Cache)
        // "You are offline" and "your session expired" both end in the cache
        // being served, and they are different sentences.
        assertThat(cached.staleReason).isNotNull()
        assertThat(cached.value.map { it.name }).containsExactly("Sport")
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = CatalogueRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        categoryDao = categoryDao,
        channelDao = channelDao,
        pager = CataloguePager(channelDao),
        io = UnconfinedTestDispatcher(),
    )

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun categoryList() = """{"items":[]}"""

    private fun channelPage(page: Int, totalPages: Int, names: List<String>): String {
        val items = names.joinToString(",") { name ->
            """
            {"id":"${UUID.randomUUID()}","source_id":"$sourceId","name":"$name",
             "position":0,"is_adult":false}
            """.trimIndent()
        }
        return """
            {"items":[$items],"page":$page,"size":200,
             "total_elements":${names.size},"total_pages":$totalPages}
        """.trimIndent()
    }

    private fun entity(name: String) = ChannelEntity(
        id = UUID.randomUUID().toString(),
        sourceId = sourceId,
        categoryId = null,
        externalId = null,
        name = name,
        logoUrl = null,
        tvgId = null,
        number = null,
        quality = null,
        position = 0,
        isAdult = false,
    )

    private fun categoryEntity(name: String) = CategoryEntity(
        id = UUID.randomUUID().toString(),
        sourceId = sourceId,
        externalId = null,
        name = name,
        contentType = "LIVE",
        position = 0,
        channelCount = null,
    )
}

/**
 * The channel table, in a list.
 *
 * `replaceForSource` is inherited from the interface rather than reimplemented:
 * its delete-then-insert is part of what these tests are checking, and a fake
 * that reimplemented it could pass while the real one was wrong.
 */
private class FakeChannelDao : ChannelDao {

    val stored = mutableListOf<ChannelEntity>()

    override suspend fun upsert(channels: List<ChannelEntity>) {
        stored += channels
    }

    override suspend fun deleteBySource(sourceId: String) {
        stored.removeAll { it.sourceId == sourceId }
    }

    override suspend fun countForSource(sourceId: String): Int =
        stored.count { it.sourceId == sourceId }

    override fun observe(id: String): Flow<ChannelEntity?> = MutableStateFlow(null)

    override fun pagedByCategory(
        sourceId: String,
        categoryId: String,
    ): PagingSource<Int, ChannelEntity> = unreachable()

    override fun pagedBySource(sourceId: String): PagingSource<Int, ChannelEntity> = unreachable()

    override fun pagedBySearch(
        sourceId: String,
        query: String,
    ): PagingSource<Int, ChannelEntity> = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("Paging is Room's, and is not what these tests are about")
}

private class FakeCategoryDao : CategoryDao {

    val stored = MutableStateFlow<List<CategoryEntity>>(emptyList())

    override fun observeBySource(
        sourceId: String,
        contentType: String,
    ): Flow<List<CategoryEntity>> = stored

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
