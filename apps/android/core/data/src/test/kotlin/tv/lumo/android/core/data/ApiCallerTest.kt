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
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * What a repository gets back, against a real HTTP stack.
 *
 * [ProblemReaderTest] covers the translation of a body in isolation; this covers
 * the four outcomes as they actually arrive — through Retrofit, through Moshi,
 * and through a socket. The two together are what let every screen in both
 * applications assume that a failure is a [LumoError] and never an exception.
 *
 * The API used is a generated one rather than a test interface written here, so
 * the test exercises the same Retrofit and Moshi configuration the applications
 * run on.
 */
class ApiCallerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SourcesApi

    private val calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build()))

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = retrofit(server.url("/").toString())
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `a body that parses comes back as a success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))

        val result = calls.call { api.listSources() }

        assertThat(result).isInstanceOf(LumoResult.Success::class.java)
        assertThat((result as LumoResult.Success).value.items).isEmpty()
    }

    @Test
    fun `a problem document becomes its code`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"type":"x","title":"x","status":409,"code":"SOURCE_NOT_READY"}"""),
        )

        val result = calls.call { api.listSources() }

        val error = (result as LumoResult.Failure).error
        assertThat(error).isInstanceOf(LumoError.Api::class.java)
        assertThat((error as LumoError.Api).code).isEqualTo(ErrorCode.SOURCE_NOT_READY)
    }

    @Test
    fun `no content is a success, not an empty body failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = calls.empty { api.deleteSource(UUID.randomUUID()) }

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
    }

    @Test
    fun `a server that cannot be reached is offline, and says so`() = runBlocking {
        // The socket is closed before the call, which is what a train tunnel
        // looks like to OkHttp. This is the one failure where offering the same
        // request again is a sensible thing to do, so it has its own case.
        server.shutdown()

        val result = calls.call { api.listSources() }

        assertThat((result as LumoResult.Failure).error)
            .isInstanceOf(LumoError.Offline::class.java)
    }

    @Test
    fun `a success body that does not match the contract is unreadable, never a crash`() =
        runBlocking {
            // `items` is an array in the contract. A string there is what a
            // misconfigured gateway — or a newer server — can produce, and it
            // must not reach a screen as an exception.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":"nope"}"""))

            val result = calls.call { api.listSources() }

            val error = (result as LumoResult.Failure).error
            assertThat(error).isInstanceOf(LumoError.Unreadable::class.java)
            // No status: the converter runs inside the call, so there is no
            // response object to read one off by the time this is caught.
            assertThat((error as LumoError.Unreadable).status).isNull()
            assertThat(error.cause).isNotNull()
        }

    private fun retrofit(baseUrl: String): SourcesApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
        .build()
        .create(SourcesApi::class.java)
}
