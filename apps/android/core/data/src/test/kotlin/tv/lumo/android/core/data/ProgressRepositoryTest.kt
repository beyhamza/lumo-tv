package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
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
import tv.lumo.android.core.data.model.WatchReport
import tv.lumo.android.core.data.repository.ProgressRepository
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * S12-02/04 over the wire: what a save carries, and what each answer to a removal
 * means (CW-26). The `204` case is the one the device check caught: read as a body
 * that never came, a confirmed removal was shown as refused.
 */
class ProgressRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ProgressRepository
    private val sourceId = UUID.randomUUID().toString()
    private val filmId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(UserdataApi::class.java)
        repository = ProgressRepository(api, ApiCaller(ProblemReader(Serializer.moshiBuilder.build())))
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `a confirmed removal is a success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.hideFilm(sourceId, filmId)

        assertThat(result).isInstanceOf(LumoResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).endsWith("/me/progress/hide")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"item_type\":\"VOD\"")
        assertThat(body).contains(filmId)
    }

    @Test
    fun `CW-26 a refused removal is a failure`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"status":500,"title":"Internal","code":"INTERNAL_ERROR"}"""),
        )

        assertThat(repository.hideFilm(sourceId, filmId)).isInstanceOf(LumoResult.Failure::class.java)
    }

    @Test
    fun `a save carries the watched segments, the observation and the session start`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(progressJson()))

        repository.save(
            sourceId = sourceId,
            filmId = filmId,
            positionMs = 1_214_000,
            durationMs = 6_240_000,
            report = WatchReport(
                observedAtMs = 1_760_000_100_000,
                sessionStartedAtMs = 1_760_000_000_000,
                segments = listOf(1_760_000_000_000L to 1_760_000_030_000L),
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"watched_segments\":[{")
        assertThat(body).contains("\"started_at\":\"2025-10-09T08:53:20Z\"")
        assertThat(body).contains("\"ended_at\":\"2025-10-09T08:53:50Z\"")
        assertThat(body).contains("\"observed_at\":\"2025-10-09T08:55:00Z\"")
        assertThat(body).contains("\"session_started_at\":\"2025-10-09T08:53:20Z\"")
    }

    @Test
    fun `a save with nothing watched omits the segments`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(progressJson()))

        repository.save(sourceId, filmId, positionMs = 1_000, durationMs = null, report = WatchReport())

        assertThat(server.takeRequest().body.readUtf8()).doesNotContain("watched_segments")
    }

    private fun progressJson() = """
        {"id":"${UUID.randomUUID()}","source_id":"$sourceId","item_type":"VOD","item_ref":"$filmId",
         "position_ms":1214000,"updated_at":"2026-10-15T10:00:00Z","watched_ms":30000,
         "hidden":false,"in_continue":true}
    """.trimIndent()
}
