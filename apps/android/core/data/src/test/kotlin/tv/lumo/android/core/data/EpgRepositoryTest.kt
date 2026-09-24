package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.repository.DefaultEpgRepository
import tv.lumo.android.core.database.dao.EpgDao
import tv.lumo.android.core.database.model.EpgChannelProgrammeRow
import tv.lumo.android.core.database.model.EpgImportStatusEntity
import tv.lumo.android.core.database.model.EpgProgrammeEntity
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.EpgAttemptStatus
import tv.lumo.android.network.generated.model.EpgMappingStatus
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * The guide's cache, and the bounded split of a batch the server refuses
 * (US-16, S9-03, C1 D2 and C1-11).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The cache is emitted first.** A screen that waited for the server would be
 * blank on a train, and blank in a way that looks like "no guide" rather than
 * "no network" — which the offline case is supposed never to look like.
 *
 * **The split ends.** A programme too large for the ceiling on its own would
 * otherwise be halved for ever. Three levels, and then the `422` is the answer,
 * with its own code, so the screen can offer a reduction.
 *
 * **The merge keeps rows apart.** A programme on the cut of a window split is
 * in both halves and must appear once; a programme shared by two channels must
 * appear under both. One `distinctBy` in the wrong place gets one of them wrong.
 *
 * **Two in flight.** Counted at the server, with a dispatcher that holds each
 * answer long enough for a third request to have shown up if there were one.
 *
 * The DAO is a fake over a list: what is under test is the reading order and
 * the arithmetic, not Room, which needs an Android runtime the build does not
 * ask for (AGENTS.md §8). Real Retrofit and Moshi, so the query — repeated
 * `channelIds`, RFC 3339 bounds — is the one the server will see.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    private val dao = FakeEpgDao()
    private val sourceId = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-09-24T20:00:00Z")
    private var clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val from = now
    private val to = now.plus(Duration.ofHours(3))

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

    // ---- the cache wins -----------------------------------------------------

    @Test
    fun `the cache is emitted first, then the server's answer, and both are written`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        dao.programmes += entity("p-old", "tvg-a", start = now.minusSeconds(600), end = now.plusSeconds(600))
        server.dispatcher = answers { channels, _, _ -> grid(channels, mapOf("a" to listOf(prog("p-new", "tvg-a")))) }

        val emissions = repository().window(sourceId, listOf(a), from, to).toList()

        assertThat(emissions).hasSize(2)
        val (cached, fresh) = emissions

        assertThat(cached.origin).isEqualTo(DataOrigin.Cache)
        assertThat(cached.value.programmesOf(a).map { it.title }).containsExactly("Programme p-old")
        // Never fetched on this device: nothing to date it by, and the cache
        // does not know the server's mapping.
        assertThat(cached.value.fetchedAt).isNull()
        assertThat(cached.value.importStatus).isNull()
        assertThat(cached.value.channels.single().mappingStatus).isNull()

        assertThat(fresh.origin).isEqualTo(DataOrigin.Network)
        assertThat(fresh.value.programmesOf(a).map { it.title }).containsExactly("Programme p-new")
        assertThat(fresh.value.fetchedAt).isEqualTo(now)
        assertThat(fresh.value.importStatus?.lastAttemptStatus).isEqualTo(EpgAttemptStatus.SUCCEEDED)
        assertThat(fresh.value.channels.single().mappingStatus).isEqualTo(EpgMappingStatus.MAPPED)

        // Written, with the status beside the rows.
        assertThat(dao.programmes.map { it.title }).containsExactly("Programme p-old", "Programme p-new")
        assertThat(dao.status[sourceId]?.fetchedAt).isEqualTo(now.toEpochMilli())
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `when the server cannot be reached the cache is served again, with the reason`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        dao.programmes += entity("p-old", "tvg-a", start = now, end = now.plusSeconds(3600))
        dao.status[sourceId] = status(fetchedAt = now.minusSeconds(7200))
        server.dispatcher = answers { _, _, _ -> MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR"}""") }

        val emissions = repository().window(sourceId, listOf(a), from, to).toList()

        assertThat(emissions).hasSize(2)
        val again = emissions.last()
        assertThat(again.origin).isEqualTo(DataOrigin.Cache)
        assertThat(again.staleReason).isNotNull()
        assertThat(again.value.programmesOf(a).map { it.title }).containsExactly("Programme p-old")
        // The date of the *last fetch*, not of this failed one.
        assertThat(again.value.fetchedAt).isEqualTo(now.minusSeconds(7200))
    }

    @Test
    fun `a channel without programmes has an entry, in the order asked, and an empty list`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        val b = channel("b", tvgId = null)
        server.dispatcher = answers { channels, _, _ ->
            grid(channels, mapOf("a" to listOf(prog("p1", "tvg-a"))), noTvg = setOf("b"))
        }

        val fresh = repository().window(sourceId, listOf(b, a), from, to).toList().last().value

        assertThat(fresh.channels.map { it.channelId }).containsExactly(b, a).inOrder()
        assertThat(fresh.programmesOf(b)).isEmpty()
        assertThat(fresh.channels.first().mappingStatus).isEqualTo(EpgMappingStatus.NO_TVG_ID)
    }

    // ---- 422, split and merge -----------------------------------------------

    @Test
    fun `a batch the server refuses is halved by channels, once, and merged in the order asked`() = runTest {
        val ids = (1..4).map { channel("c$it", tvgId = "tvg-$it") }
        server.dispatcher = answers { channels, _, _ ->
            if (channels.size > 2) {
                tooLarge()
            } else {
                grid(channels, channels.associateWith { id -> listOf(prog("p-$id", "tvg-${id.tvgSuffix()}")) })
            }
        }

        val fresh = repository().window(sourceId, ids, from, to).toList().last().value

        // One refused, two that fit: three requests, never one per channel.
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(fresh.channels.map { it.channelId }).isEqualTo(ids)
        assertThat(fresh.channels.all { it.programmes.size == 1 }).isTrue()
    }

    @Test
    fun `a single channel too large is halved by window, and a programme on the cut appears once`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        val middle = from.plus(Duration.ofMinutes(90))
        // Straddles the cut: the server sends it in both halves, full times.
        val straddling = prog("p-mid", "tvg-a", start = middle.minusSeconds(900), end = middle.plusSeconds(900))
        server.dispatcher = answers { channels, f, t ->
            when {
                t.isAfter(f.plus(Duration.ofMinutes(90))) -> tooLarge()
                t <= middle -> grid(channels, mapOf("a" to listOf(prog("p-early", "tvg-a", start = from, end = from.plusSeconds(600)), straddling)))
                else -> grid(channels, mapOf("a" to listOf(straddling, prog("p-late", "tvg-a", start = to.minusSeconds(600), end = to))))
            }
        }

        val fresh = repository().window(sourceId, listOf(a), from, to).toList().last().value

        assertThat(server.requestCount).isEqualTo(3)
        assertThat(fresh.programmesOf(a).map { it.title })
            .containsExactly("Programme p-early", "Programme p-mid", "Programme p-late")
            .inOrder()
        // One row per UUID in the cache too.
        assertThat(dao.programmes.map { it.title })
            .containsExactly("Programme p-early", "Programme p-mid", "Programme p-late")
    }

    @Test
    fun `a programme shared by two channels stays under both`() = runTest {
        val a = channel("a", tvgId = "shared")
        val b = channel("b", tvgId = "shared")
        server.dispatcher = answers { channels, _, _ ->
            if (channels.size > 1) tooLarge() else grid(channels, channels.associateWith { listOf(prog("p-shared", "shared")) })
        }

        val fresh = repository().window(sourceId, listOf(a, b), from, to).toList().last().value

        // Never deduplicated across rows: both channels show it (C1 D2).
        assertThat(fresh.programmesOf(a).map { it.title }).containsExactly("Programme p-shared")
        assertThat(fresh.programmesOf(b).map { it.title }).containsExactly("Programme p-shared")
        // And the cache holds it once, joined to both at the next read.
        assertThat(dao.programmes.map { it.title }).containsExactly("Programme p-shared")
        val cachedAgain = repository().window(sourceId, listOf(a, b), from, to).toList().first().value
        assertThat(cachedAgain.programmesOf(a).map { it.title }).containsExactly("Programme p-shared")
        assertThat(cachedAgain.programmesOf(b).map { it.title }).containsExactly("Programme p-shared")
    }

    @Test
    fun `past three levels the 422 is the answer, the cache stays, and no request is made beyond the plan`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        dao.programmes += entity("p-old", "tvg-a", start = now, end = now.plusSeconds(3600))
        server.dispatcher = answers { _, _, _ -> tooLarge() }

        val emissions = repository().window(sourceId, listOf(a), from, to).toList()

        // 1 + 2 + 4 + 8 = 15 requests for a single channel: three halvings of
        // the window, and then nothing. An unbounded planner would still be
        // running.
        assertThat(server.requestCount).isEqualTo(15)
        val last = emissions.last()
        assertThat(last.origin).isEqualTo(DataOrigin.Cache)
        assertThat((last.staleReason as LumoError.Api).code).isEqualTo(ErrorCode.EPG_WINDOW_TOO_LARGE)
        assertThat(last.value.programmesOf(a).map { it.title }).containsExactly("Programme p-old")
    }

    @Test
    fun `at most two requests are in flight at once`() = runTest {
        val ids = (1..8).map { channel("c$it", tvgId = "tvg-$it") }
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        server.dispatcher = answers { channels, _, _ ->
            val current = inFlight.incrementAndGet()
            peak.accumulateAndGet(current, ::maxOf)
            // Long enough for a third request to arrive if one were allowed.
            Thread.sleep(40)
            inFlight.decrementAndGet()
            if (channels.size > 1) tooLarge() else grid(channels, emptyMap())
        }

        repository().window(sourceId, ids, from, to).toList()

        assertThat(server.requestCount).isEqualTo(15)
        assertThat(peak.get()).isAtMost(2)
    }

    // ---- housekeeping -------------------------------------------------------

    @Test
    fun `programmes that ended before yesterday are purged on the first read, then once a day`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        dao.programmes += entity("p-ancient", "tvg-a", start = now.minus(Duration.ofDays(3)), end = now.minus(Duration.ofDays(2)))
        dao.programmes += entity("p-yesterday", "tvg-a", start = now.minus(Duration.ofHours(30)), end = now.minus(Duration.ofHours(23)))
        server.dispatcher = answers { channels, _, _ -> grid(channels, emptyMap()) }
        val repository = repository()

        repository.window(sourceId, listOf(a), from, to).toList()

        assertThat(dao.programmes.map { it.id }).containsExactly("p-yesterday")
        assertThat(dao.purges).isEqualTo(1)

        // The same day: no second purge. A day later: one.
        repository.window(sourceId, listOf(a), from, to).toList()
        assertThat(dao.purges).isEqualTo(1)
        clock = Clock.fixed(now.plus(Duration.ofHours(25)), ZoneOffset.UTC)
        repository.window(sourceId, listOf(a), from, to).toList()
        assertThat(dao.purges).isEqualTo(2)
    }

    @Test
    fun `forgetting a source drops its programmes and its status, and only its own`() = runTest {
        val other = UUID.randomUUID().toString()
        dao.programmes += entity("p-mine", "tvg-a", start = now, end = now.plusSeconds(60))
        dao.programmes += entity("p-theirs", "tvg-a", start = now, end = now.plusSeconds(60), source = other)
        dao.status[sourceId] = status(fetchedAt = now)
        dao.status[other] = status(fetchedAt = now)

        repository().forget(sourceId)

        assertThat(dao.programmes.map { it.id }).containsExactly("p-theirs")
        assertThat(dao.status.keys).containsExactly(other)
    }

    @Test
    fun `the request carries every channel id, repeated, and RFC 3339 bounds`() = runTest {
        val a = channel("a", tvgId = "tvg-a")
        val b = channel("b", tvgId = "tvg-b")
        server.dispatcher = answers { channels, _, _ -> grid(channels, emptyMap()) }

        repository().window(sourceId, listOf(a, b), from, to).toList()

        val request = server.takeRequest()
        assertThat(request.path).startsWith("/sources/$sourceId/epg?")
        assertThat(request.requestUrl!!.queryParameterValues("channelIds")).containsExactly(a, b).inOrder()
        assertThat(request.requestUrl!!.queryParameter("from")).isEqualTo("2026-09-24T20:00Z")
        assertThat(request.requestUrl!!.queryParameter("to")).isEqualTo("2026-09-24T23:00Z")
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = DefaultEpgRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        epgDao = dao,
        clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant(): Instant = clock.instant()
        },
        io = UnconfinedTestDispatcher(),
    )

    /** A channel of the fake catalogue, returning its id. */
    private fun channel(name: String, tvgId: String?): String {
        val id = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
        dao.channels[id] = sourceId to tvgId
        return id
    }

    private fun String.tvgSuffix(): String = dao.channels.getValue(this).second!!.removePrefix("tvg-")

    private fun entity(id: String, tvgId: String, start: Instant, end: Instant, source: String = sourceId) =
        EpgProgrammeEntity(
            id = id,
            sourceId = source,
            tvgId = tvgId,
            startsAt = start.toEpochMilli(),
            endsAt = end.toEpochMilli(),
            title = "Programme $id",
            description = null,
            category = null,
        )

    private fun status(fetchedAt: Instant) = EpgImportStatusEntity(
        sourceId = sourceId,
        configured = true,
        lastSuccessfulImportAt = now.minusSeconds(3600).toEpochMilli(),
        lastAttemptStartedAt = null,
        lastAttemptFinishedAt = null,
        lastAttemptStatus = "SUCCEEDED",
        generatedAt = now.toEpochMilli(),
        fetchedAt = fetchedAt.toEpochMilli(),
    )

    private class Prog(val id: String, val tvgId: String, val start: Instant, val end: Instant)

    private fun prog(id: String, tvgId: String, start: Instant = from, end: Instant = from.plusSeconds(1800)) =
        Prog(id, tvgId, start, end)

    /**
     * A server that answers from a function of the request's channel ids and
     * bounds — what a split needs to be exercised against.
     */
    private fun answers(
        answer: (channelIds: List<String>, from: Instant, to: Instant) -> MockResponse,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl!!
            return answer(
                url.queryParameterValues("channelIds").map { it!! },
                Instant.parse(url.queryParameter("from")!!.rfc3339()),
                Instant.parse(url.queryParameter("to")!!.rfc3339()),
            )
        }
    }

    /** `OffsetDateTime.toString()` drops the seconds when they are zero; `Instant.parse` wants them. */
    private fun String.rfc3339(): String =
        if (Regex("""T\d\d:\d\dZ$""").containsMatchIn(this)) replace("Z", ":00Z") else this

    private fun tooLarge() = MockResponse()
        .setResponseCode(422)
        .setBody("""{"type":"x","title":"x","status":422,"code":"EPG_WINDOW_TOO_LARGE"}""")

    private fun grid(channelIds: List<String>, programmes: Map<String, List<Prog>>, noTvg: Set<String> = emptySet()): MockResponse {
        val ids = channelIds.map { it }
        val byName = programmes.mapKeys { (name, _) -> UUID.nameUUIDFromBytes(name.toByteArray()).toString() }
        val noTvgIds = noTvg.map { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }.toSet()
        val channels = ids.joinToString(",") { id ->
            val items = (byName[id] ?: programmes[id]).orEmpty().joinToString(",") { p ->
                """{"id":"${UUID.nameUUIDFromBytes(p.id.toByteArray())}","source_id":"$sourceId","tvg_id":"${p.tvgId}",
                   "starts_at":"${p.start}","ends_at":"${p.end}","title":"Programme ${p.id}"}"""
            }
            val mapping = if (id in noTvgIds) "NO_TVG_ID" else "MAPPED"
            """{"channel_id":"$id","mapping_status":"$mapping","programmes":[$items]}"""
        }
        val body = """
            {"source_id":"$sourceId","from":"$from","to":"$to","generated_at":"$now",
             "epg":{"configured":true,"last_successful_import_at":"${now.minusSeconds(3600)}",
                    "last_attempt_started_at":null,"last_attempt_finished_at":null,
                    "last_attempt_status":"SUCCEEDED"},
             "channels":[$channels]}
        """.trimIndent()
        return MockResponse().setResponseCode(200).setBody(body)
    }
}

/**
 * The two guide tables, in lists, and the channel join in a map.
 *
 * `store` and `forget` are inherited from the interface rather than
 * reimplemented: their pairing is part of what these tests check.
 */
private class FakeEpgDao : EpgDao {

    val programmes = mutableListOf<EpgProgrammeEntity>()
    val status = mutableMapOf<String, EpgImportStatusEntity>()

    /** channel id → (source id, tvg id), standing in for the `channel` table. */
    val channels = mutableMapOf<String, Pair<String, String?>>()

    var purges = 0

    override suspend fun window(channelIds: List<String>, from: Long, to: Long): List<EpgChannelProgrammeRow> =
        channelIds.flatMap { channelId ->
            val (sourceId, tvgId) = channels[channelId] ?: return@flatMap emptyList()
            if (tvgId == null) return@flatMap emptyList()
            programmes
                .filter { it.sourceId == sourceId && it.tvgId == tvgId && it.endsAt > from && it.startsAt < to }
                .map { EpgChannelProgrammeRow(channelId, it) }
        }.sortedWith(compareBy({ it.programme.startsAt }, { it.programme.id }))

    override suspend fun upsert(programmes: List<EpgProgrammeEntity>) {
        programmes.forEach { incoming ->
            this.programmes.removeAll { it.id == incoming.id }
            this.programmes += incoming
        }
    }

    override suspend fun purgeEndedBefore(cutoff: Long) {
        purges++
        programmes.removeAll { it.endsAt < cutoff }
    }

    override suspend fun deleteBySource(sourceId: String) {
        programmes.removeAll { it.sourceId == sourceId }
    }

    override suspend fun importStatus(sourceId: String): EpgImportStatusEntity? = status[sourceId]

    override suspend fun upsertImportStatus(status: EpgImportStatusEntity) {
        this.status[status.sourceId] = status
    }

    override suspend fun deleteImportStatus(sourceId: String) {
        status.remove(sourceId)
    }
}
