package tv.lumo.android.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import retrofit2.Response
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.TokenRefreshResult
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
 * What a refresh does to the screen the user is looking at (US-04, `S2-07`).
 *
 * <h2>The half that was never joined up</h2>
 *
 * `SessionManagerTest` proves that a rejected refresh clears the session and an
 * unavailable one does not. `TokenAuthenticatorTest` proves that a 401 reaches the
 * refresh at all. Neither says what the **user** then sees, because until
 * `AppStartDecision` existed there was nothing between a cleared session and a
 * screen.
 *
 * That join is where the production bug lives. The two failures look almost
 * identical from inside the network layer — no usable token either way — and
 * treating them the same signs people out because a server restarted. These tests
 * run the whole chain: refresh → session store → `isSignedIn` → start state.
 *
 * The one thing they cannot prove is the part S2-07 puts on a real device:
 * that the encrypted store survives the process being killed. A JVM test has no
 * process to kill and no Keystore to survive it.
 */
class SessionSurvivalTest {

    @Test
    fun `a refused refresh sends the user back to the way in`() = runTest {
        // The server has revoked the device's whole chain — reuse detected, or a
        // refresh token past its life. There is nothing left to retry with, and
        // leaving the user on a catalogue that answers 401 to everything is the
        // alternative this rejects.
        val store = FakeSessionStore(tokens())
        val session = SessionManager(store, ScriptedRefresher(TokenRefreshResult.Rejected))

        decision(session).stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)

            // What `TokenAuthenticator` does on a 401, with no screen involved.
            session.refresh(staleAccessToken = null)

            assertThat(awaitItem()).isEqualTo(AppStart.SignedOut)
        }

        assertThat(store.saved).isNull()
    }

    @Test
    fun `a server that did not answer changes nothing at all`() = runTest {
        // R-18, in a test: cut the API, act, put it back. The user stays signed
        // in. Signing someone out because a request timed out is precisely the
        // bug this distinction exists to prevent, and it is invisible in
        // development — where the server is always up.
        val store = FakeSessionStore(tokens())
        val session = SessionManager(
            store,
            ScriptedRefresher(TokenRefreshResult.Unavailable(IOException("no route to host"))),
        )

        decision(session).stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)

            session.refresh(staleAccessToken = null)

            // Not "eventually Ready again" — nothing happens. A start state that
            // flickered would rebuild the navigation graph and throw the user
            // back to the first screen, which is the same damage by another road.
            expectNoEvents()
        }

        assertThat(store.saved).isNotNull()
    }

    @Test
    fun `signing out is not the same event as being signed out, and looks the same`() = runTest {
        // From the shell's side the two are indistinguishable, and that is the
        // design: one place decides where the application belongs, and it reads
        // the session rather than the reason the session went.
        val store = FakeSessionStore(tokens())
        val session = SessionManager(store, RejectingRefresher)

        decision(session).stream.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)

            session.signOut()

            assertThat(awaitItem()).isEqualTo(AppStart.SignedOut)
        }
    }

    // ---- helpers -----------------------------------------------------------

    private fun decision(session: SessionManager) = AppStartDecision(
        session = session,
        sources = SourceRepository(
            OneSourceApi,
            ApiCaller(ProblemReader(Serializer.moshiBuilder.build())),
        ),
    )

    private fun tokens() = SessionTokens(
        accessToken = "access",
        refreshToken = "refresh",
        accessTokenExpiresAt = 0L,
        userId = "user",
        deviceId = "device",
    )
}

/** An account with one source, so the signed-in answer is [AppStart.Ready]. */
private object OneSourceApi : SourcesApi {

    override suspend fun listSources(): Response<SourceList> = Response.success(
        SourceList(
            listOf(
                Source(
                    id = UUID.randomUUID(),
                    label = "Test source",
                    kind = SourceKind.M3U_URL,
                    status = SourceStatus.READY,
                    autoSync = true,
                ),
            ),
        ),
    )

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
}
