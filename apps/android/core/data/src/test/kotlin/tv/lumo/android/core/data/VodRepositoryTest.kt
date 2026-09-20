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
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.repository.VodRepository
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.VodDao
import tv.lumo.android.core.database.model.CategoryEntity
import tv.lumo.android.core.database.model.VodItemEntity
import tv.lumo.android.core.database.paging.VodPager
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * Filling the offline film catalogue (US-13).
 *
 * <h2>What is worth a test here, and what is not</h2>
 *
 * Most of this repository is [tv.lumo.android.core.data.repository.CatalogueRepository]
 * deliberately repeated — the pagination walk, the whole-or-nothing write, the
 * origin bookkeeping — and those are already proved next door. Repeating their
 * assertions here would be duplicating the tests rather than testing the code.
 *
 * Three things are genuinely new, and each one is invisible until it is wrong on
 * somebody's television:
 *
 * 1. **The category table is shared.** Channels and films live in it together,
 *    keyed by `content_type`. A refresh that replaced by source rather than by
 *    type would empty the other one's strip, and only on a source carrying both
 *    — which is most of them.
 * 2. **A synopsis is fetched once per device, not once per opening.** On an
 *    Xtream panel it is one HTTP call per film against somebody else's server.
 * 3. **A source with no films is a success.** Most M3U playlists carry none, and
 *    a banner there would tell somebody their working source is broken.
 * 4. **Whether to offer a films tab costs one request.** A phone asks it at every
 *    launch, so the answer must not be a catalogue walk — and once answered it
 *    comes out of the cache, which is what keeps the tab on a train.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    private val vodDao = FakeVodDao()
    private val categoryDao = FakeSharedCategoryDao()

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
    fun `refreshing the films leaves the channel categories alone`() = runTest {
        categoryDao.stored.value = listOf(
            categoryEntity("Sport", "LIVE"),
            categoryEntity("Généralistes", "LIVE"),
        )

        server.enqueue(json(categoryList(listOf("Action"))))
        server.enqueue(json(vodPage(page = 0, totalPages = 1, names = listOf("Le Voyage"))))

        repository().refresh(sourceId)

        // The two content types share one table. A replacement scoped to the
        // source would have wiped these, and the channel screen's category strip
        // would empty every time somebody opened the films.
        assertThat(categoryDao.stored.value.filter { it.contentType == "LIVE" }.map { it.name })
            .containsExactly("Sport", "Généralistes")
        assertThat(categoryDao.stored.value.filter { it.contentType == "VOD" }.map { it.name })
            .containsExactly("Action")
    }

    @Test
    fun `a film already carrying its synopsis is not asked for again`() = runTest {
        vodDao.stored += entity("Le Voyage").copy(plot = "Un synopsis déjà en cache")

        val id = vodDao.stored.first().id
        val result = repository().refreshFilm(id)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        // On an Xtream panel this call reaches the user's own server, one film at
        // a time. Making it again for a synopsis already on the device is a
        // request against somebody else's machine for nothing.
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a film with no cached synopsis is fetched, and the answer is written`() = runTest {
        val film = entity("Le Voyage")
        vodDao.stored += film
        server.enqueue(json(vodItem(id = film.id, name = "Le Voyage", plot = "Une traversée.")))

        val result = repository().refreshFilm(film.id)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(vodDao.stored.single { it.id == film.id }.plot).isEqualTo("Une traversée.")
    }

    @Test
    fun `a film opened before it was ever synchronised is written, not dropped`() = runTest {
        val id = UUID.randomUUID().toString()
        server.enqueue(json(vodItem(id = id, name = "Les Falaises", plot = "Un synopsis.")))

        repository().refreshFilm(id)

        // Reached by a deep link or a resume rail, with no refresh behind it. An
        // update that matched no row would leave the detail screen empty for a
        // film the server answered for.
        assertThat(vodDao.stored.map { it.name }).containsExactly("Les Falaises")
    }

    @Test
    fun `a source with no films is a success, and the grid is simply empty`() = runTest {
        server.enqueue(json(categoryList(emptyList())))
        server.enqueue(json(vodPage(page = 0, totalPages = 0, names = emptyList())))

        val repository = repository()
        val result = repository.refresh(sourceId)

        // Most M3U playlists carry no films at all. A failure here would tell
        // somebody their working source is broken.
        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(repository.categories(sourceId).first().origin).isEqualTo(DataOrigin.Network)
        assertThat(vodDao.stored).isEmpty()
    }

    @Test
    fun `a failed film refresh serves the cache and carries the reason`() = runTest {
        vodDao.stored += entity("Yesterday")
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}"""))

        val repository = repository()
        repository.refresh(sourceId)

        val cached = repository.categories(sourceId).first()

        assertThat(cached.origin).isEqualTo(DataOrigin.Cache)
        assertThat(cached.staleReason).isNotNull()
        // Nothing written. A half-applied replacement is a catalogue that is
        // neither the old one nor the new one.
        assertThat(vodDao.stored.map { it.name }).containsExactly("Yesterday")
    }

    @Test
    fun `deciding whether to offer films costs one request, not a catalogue walk`() = runTest {
        server.enqueue(json(categoryList(listOf("Action", "Comédie"))))

        val result = repository().probeFilmCategories(sourceId)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        // One, and it is the one S5-08 names. A phone decides whether to draw a
        // films tab at every launch; paying for a walk through thirty thousand
        // rows to answer it would be a synchronisation nobody asked for.
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().path).contains("contentType=VOD")
        assertThat(vodDao.stored).isEmpty()
    }

    @Test
    fun `a source whose categories say nothing offers no films`() = runTest {
        server.enqueue(json(categoryList(emptyList())))

        val repository = repository()
        repository.probeFilmCategories(sourceId)

        // False, so the tab is absent. Most M3U playlists carry only channels,
        // and a tab onto an empty grid is a promise nobody can keep.
        assertThat(repository.hasFilms(sourceId).first()).isFalse()
    }

    @Test
    fun `a source that answered once keeps its films tab offline`() = runTest {
        server.enqueue(json(categoryList(listOf("Action"))))

        val repository = repository()
        repository.probeFilmCategories(sourceId)

        // The answer comes out of Room from here on. A phone on a train that has
        // synchronised a source with films keeps the tab it earned — the flow
        // reports the cache, not the last request.
        assertThat(repository.hasFilms(sourceId).first()).isTrue()
    }

    @Test
    fun `the film count is the listing's total, asked with one row`() = runTest {
        server.enqueue(
            json("""{"items":[],"page":0,"size":1,"total_elements":1248,"total_pages":1248}"""),
        )

        assertThat(repository().filmCount(sourceId)).isEqualTo(1248)

        val request = server.takeRequest()
        assertThat(request.requestUrl?.queryParameter("size")).isEqualTo("1")
        assertThat(request.requestUrl?.queryParameter("page")).isEqualTo("0")
    }

    @Test
    fun `a source that lists no film counts zero, because the server said so`() = runTest {
        server.enqueue(
            json("""{"items":[],"page":0,"size":1,"total_elements":0,"total_pages":0}"""),
        )

        assertThat(repository().filmCount(sourceId)).isEqualTo(0)
    }

    @Test
    fun `an unknown film count is null, never zero`() = runTest {
        // Never ingested: the listing refuses, and the device holds nothing.
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"type":"x","title":"x","status":409,"code":"SOURCE_NOT_READY"}"""),
        )

        assertThat(repository().filmCount(sourceId)).isNull()
    }

    @Test
    fun `offline, the film count falls back to what the device holds`() = runTest {
        vodDao.stored += listOf(entity("Le Voyage"), entity("Le Retour"))
        server.shutdown()

        assertThat(repository().filmCount(sourceId)).isEqualTo(2)
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = VodRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        categoryDao = categoryDao,
        vodDao = vodDao,
        pager = VodPager(vodDao),
        io = UnconfinedTestDispatcher(),
    )

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun categoryList(names: List<String>): String {
        val items = names.joinToString(",") { name ->
            """
            {"id":"${UUID.randomUUID()}","source_id":"$sourceId","name":"$name",
             "content_type":"VOD","position":0}
            """.trimIndent()
        }
        return """{"items":[$items]}"""
    }

    private fun vodPage(page: Int, totalPages: Int, names: List<String>): String {
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

    private fun vodItem(id: String, name: String, plot: String) = """
        {"id":"$id","source_id":"$sourceId","name":"$name",
         "position":0,"is_adult":false,"plot":"$plot"}
    """.trimIndent()

    private fun entity(name: String) = VodItemEntity(
        id = UUID.randomUUID().toString(),
        sourceId = sourceId,
        categoryId = null,
        externalId = null,
        name = name,
        posterUrl = null,
        year = null,
        durationSeconds = null,
        rating = null,
        plot = null,
        position = 0,
        isAdult = false,
    )

    private fun categoryEntity(name: String, contentType: String) = CategoryEntity(
        id = UUID.randomUUID().toString(),
        sourceId = sourceId,
        externalId = null,
        name = name,
        contentType = contentType,
        position = 0,
        channelCount = null,
    )
}

/**
 * The film table, in a list.
 *
 * `replaceForSource` is inherited from the interface rather than reimplemented,
 * for the reason the channel fake states: its delete-then-insert is part of what
 * is under test, and a fake that reimplemented it could pass while the real one
 * was wrong.
 */
private class FakeVodDao : VodDao {

    val stored = mutableListOf<VodItemEntity>()

    override suspend fun upsert(items: List<VodItemEntity>) {
        items.forEach { item ->
            stored.removeAll { it.id == item.id }
            stored += item
        }
    }

    override suspend fun deleteBySource(sourceId: String) {
        stored.removeAll { it.sourceId == sourceId }
    }

    override suspend fun countForSource(sourceId: String): Int =
        stored.count { it.sourceId == sourceId }

    override suspend fun byIds(ids: List<String>): List<VodItemEntity> =
        stored.filter { it.id in ids }

    override fun observe(id: String): Flow<VodItemEntity?> =
        MutableStateFlow(Unit).map { stored.firstOrNull { entity -> entity.id == id } }

    override suspend fun updatePlot(id: String, plot: String?) {
        val index = stored.indexOfFirst { it.id == id }
        if (index >= 0) stored[index] = stored[index].copy(plot = plot)
    }

    override fun pagedByCategory(
        sourceId: String,
        categoryId: String,
    ): PagingSource<Int, VodItemEntity> = unreachable()

    override fun pagedBySource(sourceId: String): PagingSource<Int, VodItemEntity> = unreachable()

    override fun pagedBySearch(
        sourceId: String,
        query: String,
    ): PagingSource<Int, VodItemEntity> = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("Paging is Room's, and is not what these tests are about")
}

/**
 * The category table with both content types in it, which is the point.
 *
 * The fake next door filters nothing, because nothing there ever wrote a second
 * type. Here the type is the behaviour under test, so this one keeps it.
 */
private class FakeSharedCategoryDao : CategoryDao {

    val stored = MutableStateFlow<List<CategoryEntity>>(emptyList())

    override fun observeBySource(
        sourceId: String,
        contentType: String,
    ): Flow<List<CategoryEntity>> =
        stored.map { all ->
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
