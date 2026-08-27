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
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.DeviceRegistration
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Locale
import tv.lumo.android.network.generated.model.Platform

/**
 * Signing in (US-02), against a real HTTP stack.
 *
 * The three things worth pinning are all invisible from the screen: that a
 * success is what *stores* the session — the screen never sees a token — that a
 * refusal stores nothing, and that the rate limit arrives with the server's own
 * delay attached rather than as a generic failure.
 */
class AuthRepositoryTest {

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
    fun `a successful sign-in opens the session, and hands back no tokens`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authSession()))

        val result = repository().signIn("  someone@example.test ", "correct horse")

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(store.saved?.accessToken).isEqualTo("access-token")
        assertThat(store.saved?.refreshToken).isEqualTo("refresh-token")
        // `expires_in` is seconds and the store keeps an instant, so the hint is
        // in the future rather than being the raw 900.
        assertThat(store.saved!!.accessTokenExpiresAt).isGreaterThan(System.currentTimeMillis())
    }

    @Test
    fun `the email is trimmed and the password is not`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authSession()))

        repository().signIn("  someone@example.test ", "  a space matters  ")

        val body = server.takeRequest().body.readUtf8()
        // A keyboard's autocomplete adds a trailing space to an address often
        // enough to matter, and the server would answer INVALID_CREDENTIALS —
        // the least helpful of all the possible answers.
        assertThat(body).contains("\"email\":\"someone@example.test\"")
        // A space is a legal password character. Trimming one turns a correct
        // password into a permanently wrong one.
        assertThat(body).contains("\"password\":\"  a space matters  \"")
    }

    @Test
    fun `wrong credentials leave no session behind`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"type":"x","title":"x","status":401,"code":"INVALID_CREDENTIALS"}"""),
        )

        val result = repository().signIn("someone@example.test", "wrong")

        val error = (result as LumoResult.Failure).error as LumoError.Api
        assertThat(error.code).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
        assertThat(store.saved).isNull()
    }

    @Test
    fun `the rate limit arrives with the server's own delay`() = runBlocking {
        // Five failures earn a progressive delay (US-02). Without the header the
        // screen can only say "something went wrong", which invites the user to
        // try again at once and be refused again — extending the delay.
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "90")
                .setBody("""{"type":"x","title":"x","status":429,"code":"RATE_LIMITED"}"""),
        )

        val result = repository().signIn("someone@example.test", "wrong again")

        val error = (result as LumoResult.Failure).error as LumoError.Api
        assertThat(error.code).isEqualTo(ErrorCode.RATE_LIMITED)
        assertThat(error.retryAfterSeconds).isEqualTo(90)
    }

    @Test
    fun `a Retry-After this build cannot read becomes no delay, not a wrong one`() = runBlocking {
        // RFC 9110 also allows an HTTP date. The contract types this as seconds,
        // so a date is not understood — and the screen says "in a moment" rather
        // than inventing a number.
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "Wed, 27 Aug 2026 14:00:00 GMT")
                .setBody("""{"type":"x","title":"x","status":429,"code":"RATE_LIMITED"}"""),
        )

        val result = repository().signIn("someone@example.test", "wrong again")

        assertThat(((result as LumoResult.Failure).error as LumoError.Api).retryAfterSeconds)
            .isNull()
    }

    @Test
    fun `registering signs the user in immediately`() = runBlocking {
        // The contract is explicit that registration issues a session, which is
        // why there is no "check your email to continue" wall: the verification
        // message is sent and blocks nothing.
        server.enqueue(MockResponse().setResponseCode(201).setBody(authSession()))

        val result = repository().register(
            email = "someone@example.test",
            password = "a long enough password",
            displayName = "Someone",
            locale = Locale.FR,
        )

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(store.saved?.accessToken).isEqualTo("access-token")
    }

    @Test
    fun `the interface language is sent, because no Accept-Language header is`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authSession()))

        repository().register("someone@example.test", "a long password", "Someone", Locale.FR)

        // The contract falls back to Accept-Language and then to English, and
        // this client sends no such header — so leaving it out would record a
        // French user as English and send them English email.
        assertThat(server.takeRequest().body.readUtf8()).contains("\"locale\":\"fr\"")
    }

    @Test
    fun `a blank display name is left out rather than sent empty`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authSession()))

        repository().register("someone@example.test", "a long password", "   ", Locale.EN)

        // The field is optional in the contract. An empty string is a value, and
        // the account would be named "" on every device list.
        assertThat(server.takeRequest().body.readUtf8()).doesNotContain("display_name")
    }

    @Test
    fun `an address that already has an account leaves no session behind`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(
                    """{"type":"x","title":"x","status":409,"code":"EMAIL_ALREADY_REGISTERED"}""",
                ),
        )

        val result = repository().register("someone@example.test", "a long password", null, Locale.EN)

        val error = (result as LumoResult.Failure).error as LumoError.Api
        assertThat(error.code).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED)
        assertThat(store.saved).isNull()
    }

    // ---- helpers -----------------------------------------------------------

    private fun repository() = AuthRepository(
        api = api,
        calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        session = SessionManager(store, RejectingRefresher),
        device = FixedDevice,
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



/**
 * The device description, without an Android runtime.
 *
 * [DeviceDescriber] reads `Build` and the package manager, neither of which
 * exists in a JVM test — and neither of which is what these tests are about.
 */
private object FixedDevice : DeviceDescriber {
    override fun registration() = DeviceRegistration(
        platform = Platform.ANDROID_MOBILE,
        name = "Test device",
        model = "Test manufacturer Test device",
        appVersion = "0.1.0",
    )
}
