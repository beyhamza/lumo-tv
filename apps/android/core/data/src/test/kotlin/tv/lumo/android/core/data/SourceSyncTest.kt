package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * A manual refresh, from the socket to the outcome a screen acts on (US-024).
 *
 * <h2>Why a real HTTP stack</h2>
 *
 * The thing under test is a **header**. `Retry-After` is read off the response by
 * [ApiCaller], rides on [LumoError.Api] and comes out of [asSyncOutcome] as a
 * number of seconds — or as null, and then the screen must say "later" without
 * one. A fake `Response` built by hand would prove the mapping and none of the
 * travelling, so these go through OkHttp, Retrofit and Moshi as the applications
 * do.
 */
class SourceSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var sources: SourceRepository

    private val id = UUID.randomUUID().toString()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(SourcesApi::class.java)
        sources = SourceRepository(api, ApiCaller(ProblemReader(Serializer.moshiBuilder.build())))
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `an accepted refresh hands back the source, already pending`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{"id":"$id","label":"Source A","kind":"M3U_URL","status":"PENDING","auto_sync":true}""",
            ),
        )

        val outcome = sources.sync(id).asSyncOutcome()

        assertThat(outcome).isInstanceOf(SyncOutcome.Accepted::class.java)
        assertThat((outcome as SyncOutcome.Accepted).source.status).isEqualTo(SourceStatus.PENDING)
    }

    @Test
    fun `a refresh already running is not an error`() = runBlocking {
        server.enqueue(problem(409, "SOURCE_SYNC_IN_PROGRESS"))

        assertThat(sources.sync(id).asSyncOutcome()).isEqualTo(SyncOutcome.AlreadyRunning)
    }

    @Test
    fun `a rate limit carries the server's delay, in seconds`() = runBlocking {
        server.enqueue(problem(429, "SOURCE_SYNC_RATE_LIMITED").setHeader("Retry-After", "170"))

        val result = sources.sync(id)

        // The header reaches the typed error…
        val error = (result as LumoResult.Failure).error as LumoError.Api
        assertThat(error.code).isEqualTo(ErrorCode.SOURCE_SYNC_RATE_LIMITED)
        assertThat(error.retryAfterSeconds).isEqualTo(170)
        // …and the outcome the view model reads.
        assertThat(result.asSyncOutcome()).isEqualTo(SyncOutcome.RateLimited(170))
    }

    @Test
    fun `a rate limit without the header has no delay, and none is invented`() = runBlocking {
        server.enqueue(problem(429, "SOURCE_SYNC_RATE_LIMITED"))

        assertThat(sources.sync(id).asSyncOutcome()).isEqualTo(SyncOutcome.RateLimited(null))
    }

    @Test
    fun `a delay this build cannot read is no delay`() = runBlocking {
        // RFC 9110 allows an HTTP date. The contract types seconds, so a date is
        // not understood — and what is not understood is not shown.
        listOf("Wed, 21 Oct 2026 07:28:00 GMT", "soon", "-5", "0", "").forEach { header ->
            server.enqueue(problem(429, "SOURCE_SYNC_RATE_LIMITED").setHeader("Retry-After", header))

            assertThat(sources.sync(id).asSyncOutcome()).isEqualTo(SyncOutcome.RateLimited(null))
        }
    }

    @Test
    fun `a source deleted elsewhere is a proof, not a failed refresh`() = runBlocking {
        server.enqueue(problem(404, "SOURCE_NOT_FOUND"))

        assertThat(sources.sync(id).asSyncOutcome()).isEqualTo(SyncOutcome.Gone)
    }

    @Test
    fun `anything else is a failure that keeps its error`() = runBlocking {
        server.enqueue(problem(500, "INTERNAL_ERROR"))
        val refused = sources.sync(id).asSyncOutcome()

        server.shutdown()
        val offline = sources.sync(id).asSyncOutcome()

        assertThat((refused as SyncOutcome.Failed).error).isInstanceOf(LumoError.Api::class.java)
        assertThat((offline as SyncOutcome.Failed).error).isInstanceOf(LumoError.Offline::class.java)
    }

    @Test
    fun `a delay is worded in whole minutes, rounded up`() {
        assertThat(retryAfterMinutes(300)).isEqualTo(5)
        // 170 s said as "2 min" would earn a second refusal.
        assertThat(retryAfterMinutes(170)).isEqualTo(3)
        assertThat(retryAfterMinutes(60)).isEqualTo(1)
        assertThat(retryAfterMinutes(1)).isEqualTo(1)
        assertThat(retryAfterMinutes(null)).isNull()
        assertThat(retryAfterMinutes(0)).isNull()
    }

    private fun problem(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setBody("""{"type":"x","title":"x","status":$status,"code":"$code"}""")
}
