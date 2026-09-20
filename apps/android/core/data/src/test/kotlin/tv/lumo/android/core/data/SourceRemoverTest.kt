package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.DefaultActiveSourceRepository
import tv.lumo.android.core.data.repository.SourceRemover
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.network.generated.infrastructure.Serializer

/**
 * Deleting a source, from the request to what the device browses next (US-024).
 *
 * The rule has an order and the order has a wrong version, which is what these
 * hold: **the server first, and nothing local unless it agreed.** Then the three
 * caches, then the active source — one left is selected, several ask, none sends
 * to "add a source". The real [DefaultActiveSourceRepository] is used, because
 * "re-resolves" is a claim about it and a fake would only restate the claim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceRemoverTest {

    private val a = benchSource("Source A")
    private val b = benchSource("Source B")
    private val c = benchSource("Source C")

    private val store = InMemoryActiveSourceStore()
    private val forgotten = mutableListOf<String>()
    private var accountListReloads = 0

    /** What `DELETE` answers. The list loses the source only on a `204`. */
    private var deleteAnswer: (UUID) -> Response<Unit> = { id ->
        api.sources = api.sources.filterNot { it.id == id }
        Response.success(204, Unit)
    }

    private val api: ScriptedSourcesApi = ScriptedSourcesApi(onDelete = { id -> deleteAnswer(id) })

    @Test
    fun `deleting the active source with one left selects it, without a question`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()

        val result = remover.remove(a.key)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(active.state.value).isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(b)))
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    @Test
    fun `deleting the active source with several left asks which one`() = runTest {
        api.sources = listOf(a, b, c)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()

        remover.remove(a.key)

        assertThat(active.state.value).isEqualTo(ActiveSourceState.NeedsChoice(listOf(b, c)))
        // Never chosen in silence, so nothing is recorded either.
        assertThat(store.entries).isEmpty()
    }

    @Test
    fun `deleting the last source leaves none, which opens the add flow`() = runTest {
        api.sources = listOf(a)
        val (remover, active) = subject()

        remover.remove(a.key)

        assertThat(active.state.value).isEqualTo(ActiveSourceState.None)
        assertThat(store.entries).isEmpty()
    }

    @Test
    fun `deleting another source keeps the one being browsed`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()

        remover.remove(b.key)

        assertThat(active.state.value).isEqualTo(ActiveSourceState.Selected(a.key, a, listOf(a)))
        assertThat(store.entries).containsExactly("user-1", a.key)
    }

    @Test
    fun `the three caches of the deleted source are dropped, and only its own`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, _) = subject()

        remover.remove(b.key)

        // Channels, films, series — one entry per cache, all for the same source.
        assertThat(forgotten).containsExactly(b.key, b.key, b.key)
        // Favourites and recent channels went with the cascade: re-read.
        assertThat(accountListReloads).isEqualTo(2)
    }

    @Test
    fun `a refused deletion is reported and changes nothing on the device`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()
        val before = active.state.value
        deleteAnswer = { _ -> Response.error(500, problem("INTERNAL_ERROR")) }

        val result = remover.remove(a.key)

        assertThat(result).isInstanceOf(LumoResult.Failure::class.java)
        assertThat(forgotten).isEmpty()
        assertThat(accountListReloads).isEqualTo(0)
        assertThat(active.state.value).isEqualTo(before)
        assertThat(store.entries).containsExactly("user-1", a.key)
    }

    @Test
    fun `a source already deleted elsewhere is a deletion that succeeded`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()
        // Another device got there first: the list no longer has it, and the
        // server says so by name.
        deleteAnswer = { id ->
            api.sources = api.sources.filterNot { it.id == id }
            Response.error(404, problem("SOURCE_NOT_FOUND"))
        }

        val result = remover.remove(a.key)

        assertThat(result).isEqualTo(LumoResult.Success(Unit))
        assertThat(forgotten).containsExactly(a.key, a.key, a.key)
        assertThat(active.state.value.selectedSourceId).isEqualTo(b.key)
    }

    @Test
    fun `a list that still carries the deleted source cannot bring it back`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val (remover, active) = subject()
        // The delete is acknowledged, and a read a moment later still lists it.
        deleteAnswer = { _ -> Response.success(204, Unit) }

        remover.remove(a.key)

        assertThat(active.state.value.selectedSourceId).isEqualTo(b.key)
    }

    // ---- helpers -----------------------------------------------------------

    private fun TestScope.subject(): Pair<SourceRemover, DefaultActiveSourceRepository> {
        val sources = SourceRepository(api, ApiCaller(ProblemReader(Serializer.moshiBuilder.build())))
        val active = DefaultActiveSourceRepository(
            session = SessionManager(FakeSessionStore(tokens("user-1")), RejectingRefresher),
            sources = sources,
            store = store,
            scope = backgroundScope,
        )
        runCurrent()

        val forget: suspend (String) -> Unit = { sourceId -> forgotten += sourceId }
        val reload: suspend () -> Unit = { accountListReloads++ }

        val remover = SourceRemover(
            delete = { sourceId -> sources.delete(sourceId) },
            // Channels, films and series; then favourites and recent channels.
            caches = listOf(forget, forget, forget),
            accountLists = listOf(reload, reload),
            activeSource = active,
        )
        return remover to active
    }

    private fun problem(code: String) =
        """{"type":"x","title":"x","status":0,"code":"$code"}"""
            .toResponseBody("application/problem+json".toMediaType())

    private fun tokens(userId: String) = SessionTokens(
        accessToken = "access",
        refreshToken = "refresh",
        accessTokenExpiresAt = 0L,
        userId = userId,
        deviceId = "device",
    )
}
