package tv.lumo.android.core.auth

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.auth.store.SessionStore

/**
 * The single-flight refresh rule (US-04).
 *
 * These are the tests that matter in this module: everything else here is
 * storage. If two refreshes can ever be in flight at once, the second presents a
 * token the first has already rotated, the server treats it as theft and revokes
 * the device chain — and the user is signed out for opening the app.
 */
class SessionManagerTest {

    @Test
    fun `concurrent refreshes result in a single call to the server`() = runTest {
        val store = FakeSessionStore(session(access = "expired", refresh = "r1"))
        val refresher = CountingRefresher(
            result = TokenRefreshResult.Rotated("fresh", "r2", expiresInSeconds = 900),
            latency = 50,
        )
        val manager = SessionManager(store, refresher)

        // Eight screens wake up at once, all holding the same expired token.
        val outcomes = List(8) { async { manager.refresh(staleAccessToken = "expired") } }.awaitAll()

        assertThat(refresher.calls.get()).isEqualTo(1)
        assertThat(outcomes).hasSize(8)
        outcomes.forEach {
            assertThat(it).isInstanceOf(RefreshOutcome.Refreshed::class.java)
            assertThat((it as RefreshOutcome.Refreshed).tokens.accessToken).isEqualTo("fresh")
        }
        assertThat(store.current()?.refreshToken).isEqualTo("r2")
    }

    @Test
    fun `a caller holding an already-rotated token does not refresh again`() = runTest {
        val store = FakeSessionStore(session(access = "current", refresh = "r1"))
        val refresher = CountingRefresher(TokenRefreshResult.Rotated("new", "r2", 900))
        val manager = SessionManager(store, refresher)

        // This caller's request failed with a token that is no longer the
        // stored one, so the session was refreshed while it was in flight.
        val outcome = manager.refresh(staleAccessToken = "some-older-token")

        assertThat(refresher.calls.get()).isEqualTo(0)
        assertThat(outcome).isEqualTo(RefreshOutcome.Refreshed(store.current()!!))
    }

    @Test
    fun `a rejected refresh token ends the session`() = runTest {
        val store = FakeSessionStore(session(access = "expired", refresh = "stolen"))
        val manager = SessionManager(store, CountingRefresher(TokenRefreshResult.Rejected))

        val outcome = manager.refresh(staleAccessToken = "expired")

        assertThat(outcome).isEqualTo(RefreshOutcome.SignedOut)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `a network failure keeps the session intact`() = runTest {
        val stored = session(access = "expired", refresh = "r1")
        val store = FakeSessionStore(stored)
        val manager = SessionManager(
            store,
            CountingRefresher(TokenRefreshResult.Unavailable(java.io.IOException("offline"))),
        )

        val outcome = manager.refresh(staleAccessToken = "expired")

        assertThat(outcome).isInstanceOf(RefreshOutcome.Unavailable::class.java)
        // A tunnel is not a theft. The user stays signed in.
        assertThat(store.current()).isEqualTo(stored)
    }

    @Test
    fun `refreshing without a session reports signed out`() = runTest {
        val manager = SessionManager(FakeSessionStore(null), CountingRefresher(TokenRefreshResult.Rejected))

        assertThat(manager.refresh(staleAccessToken = null)).isEqualTo(RefreshOutcome.SignedOut)
    }

    @Test
    fun `signing out clears the stored session`() = runTest {
        val store = FakeSessionStore(session("a", "r"))
        val manager = SessionManager(store, CountingRefresher(TokenRefreshResult.Rejected))

        manager.signOut()

        assertThat(store.current()).isNull()
        assertThat(manager.isSignedIn.first()).isFalse()
    }

    private fun session(access: String, refresh: String) = SessionTokens(
        accessToken = access,
        refreshToken = refresh,
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

    private class CountingRefresher(
        private val result: TokenRefreshResult,
        private val latency: Long = 0,
    ) : TokenRefresher {
        val calls = AtomicInteger(0)

        override suspend fun refresh(refreshToken: String): TokenRefreshResult {
            calls.incrementAndGet()
            if (latency > 0) delay(latency)
            return result
        }
    }
}
