package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ConnectivityMonitor
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.core.data.repository.DefaultWatchlistRepository
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * "My list" over the wire (US-022, ADR 0011): the mapping of a page, the walk that
 * answers "is this film in my list", and — the part that matters — what each answer
 * to a write **means**.
 *
 * The outcome table is the product's, not an implementation detail: an unknown
 * result that read as a success would announce a change nobody can vouch for, and
 * a `404` on a removal that read as a failure would show an error for the very
 * state the user asked for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: UserdataApi

    private val sourceId = UUID.randomUUID().toString()
    private val itemId = UUID.randomUUID().toString()
    private val contentId = UUID.randomUUID().toString()
    private val key = UUID.randomUUID()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(UserdataApi::class.java)
    }

    @After
    fun stop() = server.shutdown()

    // ---- reads ---------------------------------------------------------------

    @Test
    fun `a page keeps the server order, the source filter and the list-wide types`() = runTest {
        val newer = UUID.randomUUID().toString()
        val older = UUID.randomUUID().toString()
        server.enqueue(
            json(
                page(
                    items = listOf(item(newer, "VOD", "2026-10-14T10:00:00Z"), item(older, "SERIES", "2026-10-13T10:00:00Z")),
                    types = """["VOD","SERIES"]""",
                ),
            ),
        )

        val slice = (repository().page(sourceId, type = WatchlistType.Film) as LumoResult.Success).value

        val request = server.takeRequest()
        assertThat(request.requestUrl?.queryParameter("sourceId")).isEqualTo(sourceId)
        assertThat(request.requestUrl?.queryParameter("contentType")).isEqualTo("VOD")
        assertThat(slice.items.map { it.id }).containsExactly(newer, older).inOrder()
        assertThat(slice.types).containsExactly(WatchlistType.Film, WatchlistType.Series)
        assertThat(slice.hasMore).isFalse()
    }

    @Test
    fun `an unavailable item keeps its title and has nothing to open`() = runTest {
        server.enqueue(
            json(
                page(
                    items = listOf(
                        """{"id":"$itemId","source_id":"$sourceId","content_type":"VOD",
                            "available":false,"title":"Film disparu","content_id":null,
                            "poster_url":null,"added_at":"2026-10-14T10:00:00Z"}""",
                    ),
                ),
            ),
        )

        val entry = (repository().page(sourceId) as LumoResult.Success).value.items.single()

        assertThat(entry.available).isFalse()
        assertThat(entry.contentId).isNull()
        assertThat(entry.title).isEqualTo("Film disparu")
        assertThat(entry.posterUrl).isNull()
    }

    @Test
    fun `membership walks the pages at the largest size until it finds the content`() = runTest {
        server.enqueue(json(page(items = listOf(item(UUID.randomUUID().toString(), "VOD")), page = 0, totalPages = 2)))
        server.enqueue(json(page(items = listOf(item(itemId, "VOD", content = contentId)), page = 1, totalPages = 2)))

        val found = repository().find(sourceId, WatchlistType.Film, contentId)

        assertThat((found as LumoResult.Success).value?.id).isEqualTo(itemId)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertThat(first.requestUrl?.queryParameter("size")).isEqualTo("200")
        assertThat(first.requestUrl?.queryParameter("page")).isEqualTo("0")
        assertThat(second.requestUrl?.queryParameter("page")).isEqualTo("1")
    }

    @Test
    fun `membership answers null at the end of the list, and stops on an empty page`() = runTest {
        // Claims three pages, delivers an empty second one: the walk stops there.
        server.enqueue(json(page(items = listOf(item(UUID.randomUUID().toString(), "VOD")), totalPages = 3)))
        server.enqueue(json(page(items = emptyList(), page = 1, totalPages = 3)))

        val found = repository().find(sourceId, WatchlistType.Film, contentId)

        assertThat(found).isEqualTo(LumoResult.Success(null))
        assertThat(server.requestCount).isEqualTo(2)
    }

    // ---- writes: the key -----------------------------------------------------

    @Test
    fun `an add sends the intention's key and the content, never a source`() = runTest {
        server.enqueue(json(item(itemId, "SERIES", content = contentId), code = 201))

        repository().write(add())

        val request = server.takeRequest()
        assertThat(request.getHeader("Idempotency-Key")).isEqualTo(key.toString())
        assertThat(request.body.readUtf8()).apply {
            contains("\"content_type\":\"SERIES\"")
            contains(contentId)
            doesNotContain("source_id")
        }
    }

    @Test
    fun `a removal addresses the item and sends the intention's key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        repository().write(remove())

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).endsWith("/me/watchlist/$itemId")
        assertThat(request.getHeader("Idempotency-Key")).isEqualTo(key.toString())
    }

    // ---- writes: the outcomes ------------------------------------------------

    @Test
    fun `201 on an add is confirmed, with the item`() = runTest {
        server.enqueue(json(item(itemId, "SERIES", content = contentId), code = 201))

        val outcome = repository().write(add())

        assertThat(outcome).isInstanceOf(WatchlistWriteOutcome.Confirmed::class.java)
        assertThat((outcome as WatchlistWriteOutcome.Confirmed).item?.id).isEqualTo(itemId)
    }

    @Test
    fun `200 on an add means it was already there`() = runTest {
        server.enqueue(json(item(itemId, "SERIES", content = contentId), code = 200))

        val outcome = repository().write(add())

        assertThat(outcome).isInstanceOf(WatchlistWriteOutcome.AlreadyInState::class.java)
        assertThat((outcome as WatchlistWriteOutcome.AlreadyInState).item?.id).isEqualTo(itemId)
    }

    @Test
    fun `204 on a removal is confirmed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertThat(repository().write(remove())).isEqualTo(WatchlistWriteOutcome.Confirmed(null))
    }

    @Test
    fun `404 WATCHLIST_ITEM_NOT_FOUND on a removal means it is already gone`() = runTest {
        server.enqueue(problem(404, "WATCHLIST_ITEM_NOT_FOUND"))

        assertThat(repository().write(remove())).isEqualTo(WatchlistWriteOutcome.AlreadyInState(null))
    }

    @Test
    fun `a lost answer is unknown, never a success`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val outcome = repository().write(add())

        assertThat(outcome).isInstanceOf(WatchlistWriteOutcome.Unknown::class.java)
        assertThat((outcome as WatchlistWriteOutcome.Unknown).cause).isInstanceOf(LumoError.Offline::class.java)
    }

    @Test
    fun `a 5xx is unknown, problem document or not`() = runTest {
        server.enqueue(problem(500, "INTERNAL_ERROR"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>bad gateway</html>"))

        assertThat(repository().write(remove())).isInstanceOf(WatchlistWriteOutcome.Unknown::class.java)
        assertThat(repository().write(remove())).isInstanceOf(WatchlistWriteOutcome.Unknown::class.java)
    }

    @Test
    fun `a reused key is a client bug, treated as unknown so the list is re-read`() = runTest {
        server.enqueue(problem(409, "IDEMPOTENCY_KEY_REUSED"))

        assertThat(repository().write(add())).isInstanceOf(WatchlistWriteOutcome.Unknown::class.java)
    }

    @Test
    fun `a content the account does not have is a refusal`() = runTest {
        server.enqueue(problem(404, "SERIES_NOT_FOUND"))
        server.enqueue(problem(404, "WATCHLIST_ITEM_NOT_FOUND"))

        // The removal's "already gone" code means nothing on an add.
        assertThat(repository().write(add())).isInstanceOf(WatchlistWriteOutcome.Rejected::class.java)
        assertThat(repository().write(add())).isInstanceOf(WatchlistWriteOutcome.Rejected::class.java)
    }

    // ---- helpers -------------------------------------------------------------

    private fun repository() = DefaultWatchlistRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        connectivity = object : ConnectivityMonitor {
            override val regained: Flow<Unit> = emptyFlow()
        },
        io = UnconfinedTestDispatcher(),
    )

    private fun add() = PendingIntention(
        key = key,
        action = WatchlistAction.Add,
        target = WatchlistTarget.Content(WatchlistType.Series, contentId),
    )

    private fun remove() = PendingIntention(
        key = key,
        action = WatchlistAction.Remove,
        target = WatchlistTarget.Item(itemId),
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun problem(code: Int, errorCode: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"t","status":$code,"code":"$errorCode"}""")

    private fun item(
        id: String,
        type: String,
        addedAt: String = "2026-10-14T10:00:00Z",
        content: String = UUID.randomUUID().toString(),
    ) = """
        {"id":"$id","source_id":"$sourceId","content_type":"$type","available":true,
         "title":"Titre","content_id":"$content","poster_url":null,"added_at":"$addedAt"}
    """.trimIndent()

    private fun page(
        items: List<String>,
        page: Int = 0,
        totalPages: Int = 1,
        types: String = """["VOD"]""",
    ) = """
        {"items":[${items.joinToString(",")}],"page":$page,"size":50,
         "total_elements":${items.size},"total_pages":$totalPages,"content_types":$types}
    """.trimIndent()
}
