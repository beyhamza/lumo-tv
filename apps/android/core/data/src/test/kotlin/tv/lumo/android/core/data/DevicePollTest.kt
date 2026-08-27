package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.DeviceDescriber
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.AuthRepository
import tv.lumo.android.core.data.repository.DevicePoll
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.DeviceRegistration
import tv.lumo.android.network.generated.model.Platform

/**
 * The television's polling states (US-05, RFC 8628).
 *
 * The whole flow lives or dies on one distinction the HTTP layer cannot make for
 * itself: **three of these `400`s are not failures.** `AUTHORIZATION_PENDING` is
 * the answer to nearly every poll this screen ever makes, `SLOW_DOWN` means keep
 * going more slowly, and `EXPIRED_TOKEN` means start over without telling anyone.
 * A client that treated them as errors would show a red message on a working
 * screen, stop polling, or leave a dead code on a television nobody is standing
 * in front of.
 */
class DevicePollTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi
    private lateinit var store: FakeSessionStore

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .addConverterFactory(MoshiConverterFactory.create(Serializer.moshiBuilder.build()))
            .build()
            .create(AuthApi::class.java)
        store = FakeSessionStore()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `pending is the nominal answer and opens nothing`() = runBlocking {
        val poll = poll("AUTHORIZATION_PENDING")

        assertThat(poll).isEqualTo(DevicePoll.Pending)
        assertThat(store.saved).isNull()
    }

    @Test
    fun `slow down is a pace, not a refusal`() = runBlocking {
        assertThat(poll("SLOW_DOWN")).isEqualTo(DevicePoll.SlowDown)
    }

    @Test
    fun `an expired or unknown code asks for a new one, and they are the same answer`() =
        runBlocking {
            // Both mean the code on screen is dead. The television replaces it
            // without anybody pressing anything — the person walked away to fetch
            // their phone, and a screen showing a dead code has stopped working
            // without saying so.
            assertThat(poll("EXPIRED_TOKEN")).isEqualTo(DevicePoll.NeedsNewCode)
            assertThat(poll("DEVICE_CODE_NOT_FOUND")).isEqualTo(DevicePoll.NeedsNewCode)
        }

    @Test
    fun `a refusal stops, and is the only one of the four that does`() = runBlocking {
        assertThat(poll("ACCESS_DENIED")).isEqualTo(DevicePoll.Denied)
    }

    @Test
    fun `approval opens the session, here and nowhere else`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authSession()))

        val poll = repository().pollDeviceToken("device-code")

        assertThat(poll).isEqualTo(DevicePoll.Approved)
        // The fourth road that ends in a stored session, and it stores it the
        // same way the other three do.
        assertThat(store.saved?.accessToken).isEqualTo("access-token")
    }

    @Test
    fun `a poll that never left the device is not a refusal`() = runBlocking {
        // The code on screen is still valid and the next attempt may well
        // succeed. Stopping here would strand a television on a code that would
        // have worked.
        server.shutdown()

        val poll = repository().pollDeviceToken("device-code")

        assertThat(poll).isInstanceOf(DevicePoll.Unavailable::class.java)
        assertThat(store.saved).isNull()
    }

    @Test
    fun `a code this build does not know keeps the screen waiting`() = runBlocking {
        // New codes may appear within v1. The cautious answer is to keep the code
        // and keep polling, not to throw away a screen that may be one approval
        // from working.
        assertThat(poll("AUTHORIZATION_SUSPENDED"))
            .isInstanceOf(DevicePoll.Unavailable::class.java)
    }

    // ---- helpers -----------------------------------------------------------

    private suspend fun poll(code: String): DevicePoll {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"type":"x","title":"x","status":400,"code":"$code"}"""),
        )
        return repository().pollDeviceToken("device-code")
    }

    private fun repository() = AuthRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        session = SessionManager(store, RejectingRefresher),
        device = FixedTvDevice,
    )

    private fun authSession(): String {
        val now = OffsetDateTime.now().toString()
        return """
            {
              "access_token": "access-token",
              "token_type": "Bearer",
              "expires_in": 900,
              "refresh_token": "refresh-token",
              "device_id": "${UUID.randomUUID()}",
              "user": {
                "id": "${UUID.randomUUID()}",
                "email": "someone@example.test",
                "locale": "fr",
                "created_at": "$now",
                "updated_at": "$now"
              }
            }
        """.trimIndent()
    }
}

private object FixedTvDevice : DeviceDescriber {
    override fun registration() = DeviceRegistration(
        platform = Platform.ANDROID_TV,
        name = "Test television",
        model = "Test manufacturer Test television",
        appVersion = "0.1.0",
    )
}
