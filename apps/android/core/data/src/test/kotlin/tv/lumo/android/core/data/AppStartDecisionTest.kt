package tv.lumo.android.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.TokenRefreshResult
import tv.lumo.android.core.auth.TokenRefresher
import tv.lumo.android.core.auth.store.SessionStore
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.network.generated.api.SourcesApi
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.CreateSourceRequest
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceList
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.UpdateSourceRequest

/**
 * Where the applications open, which is the one piece of logic behind S2-02.
 *
 * Worth a test rather than a look, because three of the four answers are only
 * ever seen under conditions that are awkward to reproduce by hand — a fresh
 * account with no source, a session that disappears while the application is
 * open, and a launch with no network.
 */
class AppStartDecisionTest {

    @Test
    fun `no session opens on the way in, after saying it does not know yet`() = runTest {
        val decision = decision(signedIn = false)

        decision.stream.test {
            // Loading first, always. The alternative is to assume "signed out"
            // for one frame, which shows the onboarding screen to a signed-in
            // user on every single launch.
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.SignedOut)
        }
    }

    @Test
    fun `signed in with no source opens on the screen that fixes that`() = runTest {
        val decision = decision(signedIn = true, sources = emptyList())

        decision.stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.NeedsSource)
        }
    }

    @Test
    fun `signed in with a source opens on the catalogue`() = runTest {
        val decision = decision(signedIn = true, sources = listOf(source()))

        decision.stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
        }
    }

    @Test
    fun `an unreachable server opens on the catalogue, not on the source form`() = runTest {
        // The two mistakes are not symmetric. Sending someone who has sources to
        // "add a source" because the network blinked is a wrong screen and an
        // alarming one; sending someone who has none to a catalogue shows an
        // empty state that tells them what to do. And the catalogue is
        // offline-first, so it may have something to show regardless.
        val decision = decision(signedIn = true, failing = true)

        decision.stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
        }
    }

    @Test
    fun `losing the session moves the user, and regaining it moves them back`() = runTest {
        val store = FakeSessionStore(tokens())
        val decision = decision(store = store, sources = listOf(source()))

        decision.stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)

            // A token reuse detected server-side revokes the device chain and
            // the session is dropped without any screen asking. Nobody is left
            // sitting on a catalogue that answers 401 to everything.
            store.clear()
            assertThat(awaitItem()).isEqualTo(AppStart.SignedOut)

            store.save(tokens())
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
        }
    }

    @Test
    fun `a token rotation does not re-decide anything`() = runTest {
        // This is the guard that matters most in practice. `isSignedIn` maps over
        // the stored session, so it re-emits on every write — and a rotation is a
        // write, roughly hourly, while the application is being used. Both shells
        // rebuild their navigation graph when the start state changes, so an
        // unfiltered re-emission would throw the user back to the first screen
        // mid-session.
        val store = FakeSessionStore(tokens(access = "first"))
        val api = CountingSourcesApi(listOf(source()))
        val decision = decision(store = store, api = api)

        decision.stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)

            store.save(tokens(access = "rotated"))
            store.save(tokens(access = "rotated again"))

            expectNoEvents()
        }

        // And the source lookup ran once, not three times: the filter is on the
        // session flow, before the network call, not on the result after it.
        assertThat(api.calls).isEqualTo(1)
    }

    // ---- helpers -----------------------------------------------------------

    private fun decision(
        signedIn: Boolean = true,
        sources: List<Source> = emptyList(),
        failing: Boolean = false,
        store: SessionStore = FakeSessionStore(if (signedIn) tokens() else null),
        api: SourcesApi = CountingSourcesApi(sources, failing),
    ): AppStartDecision {
        val calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build()))
        return AppStartDecision(
            session = SessionManager(store, RejectingRefresher),
            sources = SourceRepository(api, calls),
        )
    }

    private fun tokens(access: String = "access") = SessionTokens(
        accessToken = access,
        refreshToken = "refresh",
        accessTokenExpiresAt = 0L,
        userId = "user",
        deviceId = "device",
    )

    private fun source() = Source(
        id = UUID.randomUUID(),
        label = "Test source",
        kind = SourceKind.M3U_URL,
        status = SourceStatus.READY,
        autoSync = true,
    )
}

/** The store `SessionManager` is built on, in memory. */
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

/** Never called here: nothing in these tests provokes a refresh. */
private object RejectingRefresher : TokenRefresher {
    override suspend fun refresh(refreshToken: String): TokenRefreshResult =
        TokenRefreshResult.Rejected
}

/**
 * `GET /sources`, answering from a list — and counting, which is the whole point
 * of the rotation test.
 *
 * The other five operations are unreachable from [AppStartDecision]; they are
 * present because the generated interface declares them, and they fail loudly
 * rather than quietly if that ever stops being true.
 */
private class CountingSourcesApi(
    private val sources: List<Source>,
    private val failing: Boolean = false,
) : SourcesApi {

    var calls = 0
        private set

    override suspend fun listSources(): Response<SourceList> {
        calls++
        return if (failing) {
            Response.error(503, """{"code":"INTERNAL_ERROR"}""".toResponseBody(problemJson))
        } else {
            Response.success(SourceList(sources))
        }
    }

    override suspend fun createSource(createSourceRequest: CreateSourceRequest) = unreachable()

    override suspend fun deleteSource(id: UUID) = unreachable()

    override suspend fun getSource(id: UUID) = unreachable()

    override suspend fun syncSource(id: UUID) = unreachable()

    override suspend fun updateSource(
        id: UUID,
        updateSourceRequest: UpdateSourceRequest,
    ) = unreachable()

    private fun unreachable(): Nothing =
        throw AssertionError("AppStartDecision must only read the source list")

    private companion object {
        val problemJson = "application/problem+json".toMediaType()
    }
}
