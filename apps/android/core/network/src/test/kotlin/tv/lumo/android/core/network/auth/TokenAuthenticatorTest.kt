package tv.lumo.android.core.network.auth

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.TokenRefreshResult
import tv.lumo.android.core.auth.TokenRefresher
import tv.lumo.android.core.auth.store.SessionStore

/**
 * The 401 → refresh → replay path, against a real HTTP stack.
 *
 * [tv.lumo.android.core.auth.SessionManagerTest] proves the single-flight rule
 * in isolation; this proves the wiring actually reaches it — that the
 * interceptor tags the token it used, that OkHttp invokes the authenticator, and
 * that the replay carries the new token. Between the two, US-04's "un seul
 * refresh en vol" is covered end to end without an emulator.
 */
class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        // 401 for the stale token, 200 for anything newer. That is what a real
        // API does, and it is what makes the replay observable.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == "Bearer $EXPIRED") {
                    MockResponse().setResponseCode(401)
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"items":[]}""")
                }
        }
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `an expired token is refreshed once and the request is replayed`() {
        val store = FakeSessionStore(tokens(EXPIRED))
        val refresher = CountingRefresher()
        val client = clientFor(SessionManager(store, refresher))

        val response = client.newCall(Request.Builder().url(server.url("/sources")).build()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(refresher.calls.get()).isEqualTo(1)

        val sent = listOf(server.takeRequest(), server.takeRequest())
        assertThat(sent[0].getHeader("Authorization")).isEqualTo("Bearer $EXPIRED")
        assertThat(sent[1].getHeader("Authorization")).isEqualTo("Bearer $FRESH")
        response.close()
    }

    @Test
    fun `four requests failing at once still cause a single refresh`() = runBlocking {
        val store = FakeSessionStore(tokens(EXPIRED))
        val refresher = CountingRefresher(latencyMillis = 40)
        val client = clientFor(SessionManager(store, refresher))

        val responses = List(4) {
            async {
                client.newCall(Request.Builder().url(server.url("/sources")).build()).execute()
            }
        }.awaitAll()

        responses.forEach { assertThat(it.code).isEqualTo(200) }
        // The whole point. Four refreshes would rotate four times; the server
        // would see a consumed refresh token, assume theft, and revoke the
        // device chain — signing the user out for opening the app (US-04).
        assertThat(refresher.calls.get()).isEqualTo(1)
        assertThat(store.current()?.refreshToken).isEqualTo("refresh-2")
        responses.forEach { it.close() }
    }

    @Test
    fun `a public endpoint carries no bearer token`() {
        val store = FakeSessionStore(tokens(EXPIRED))
        val client = clientFor(SessionManager(store, CountingRefresher()))

        client.newCall(Request.Builder().url(server.url("/auth/login")).build()).execute().close()

        // Sending an access token to /auth/login is at best noise, and at worst
        // a token in somebody else's log.
        assertThat(server.takeRequest().getHeader("Authorization")).isNull()
    }

    private fun clientFor(sessionManager: SessionManager): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionManager))
        .authenticator(TokenAuthenticator(sessionManager))
        .build()

    private fun tokens(accessToken: String) = SessionTokens(
        accessToken = accessToken,
        refreshToken = "refresh-1",
        accessTokenExpiresAt = 0L,
        userId = "11111111-1111-1111-1111-111111111111",
        deviceId = "22222222-2222-2222-2222-222222222222",
    )

    private class FakeSessionStore(initial: SessionTokens?) : SessionStore {
        private val state = MutableStateFlow(initial)
        override val sessions: Flow<SessionTokens?> = state
        override suspend fun current(): SessionTokens? = state.value
        override suspend fun save(tokens: SessionTokens) {
            state.value = tokens
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private class CountingRefresher(private val latencyMillis: Long = 0) : TokenRefresher {
        val calls = AtomicInteger(0)

        override suspend fun refresh(refreshToken: String): TokenRefreshResult {
            calls.incrementAndGet()
            if (latencyMillis > 0) delay(latencyMillis)
            return TokenRefreshResult.Rotated(FRESH, "refresh-2", expiresInSeconds = 900)
        }
    }

    private companion object {
        const val EXPIRED = "expired-access-token"
        const val FRESH = "fresh-access-token"
    }
}
