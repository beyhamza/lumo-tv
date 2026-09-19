package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ProblemReader
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.DefaultActiveSourceRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.network.generated.infrastructure.Serializer
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * The active source, across what a device actually lives through (US-018).
 *
 * `ActiveSourceResolverTest` pins the rule. This pins what surrounds it and what
 * a pure function cannot show: that a decision is **recorded** when it should be
 * and only then, that a failure leaves the record alone, and that two people
 * sharing a television never meet each other's choice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSourceRepositoryTest {

    private val a = benchSource("Source A")
    private val b = benchSource("Source B")
    private val c = benchSource("Source C")

    private val api = ScriptedSourcesApi()
    private val store = InMemoryActiveSourceStore()
    private val sessions = FakeSessionStore(tokens("user-1"))

    @Test
    fun `the only source is selected and remembered without a question`() = runTest {
        api.sources = listOf(a)
        val repository = repository()

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Selected(a.key, a, listOf(a)))
        // Recorded, and that is what protects it the day a second source appears.
        assertThat(store.entries).containsExactly("user-1", a.key)
    }

    @Test
    fun `several sources and no choice asks, and records nothing until answered`() = runTest {
        api.sources = listOf(a, b)
        val repository = repository()

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.NeedsChoice(listOf(a, b)))
        assertThat(store.entries).isEmpty()

        repository.select(b.key)

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(a, b)))
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    @Test
    fun `the choice is found again at the next launch`() = runTest {
        api.sources = listOf(a, b)
        repository().select(b.key)

        // A new process: a new repository over the same device storage.
        val relaunched = repository()

        assertThat(relaunched.state.value.selectedSourceId).isEqualTo(b.key)
    }

    @Test
    fun `changing source applies at once and needs no request`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val repository = repository()
        val callsBefore = api.calls

        repository.select(b.key)

        assertThat(repository.state.value.selectedSourceId).isEqualTo(b.key)
        // Immediate is the acceptance criterion, and a round trip is not immediate
        // on the connection a television in a bedroom has.
        assertThat(api.calls).isEqualTo(callsBefore)
    }

    // ---- an unavailable server proves nothing --------------------------------

    @Test
    fun `a failed fetch at launch keeps the stored choice`() = runTest {
        api.failing = true
        store.entries["user-1"] = b.key
        val repository = repository()

        assertThat(repository.state.value)
            .isEqualTo(ActiveSourceState.Selected(b.key, source = null, sources = emptyList()))
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    @Test
    fun `a failed refresh changes neither the screen nor the record`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = b.key
        val repository = repository()
        val before = repository.state.value

        api.failing = true
        repository.refresh()

        assertThat(repository.state.value).isSameInstanceAs(before)
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    @Test
    fun `a failure that is not SOURCE_NOT_FOUND is not a deletion`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val repository = repository()

        val failures = listOf(
            LumoError.Offline(java.io.IOException("timeout")),
            LumoError.Api(ErrorCode.INTERNAL_ERROR, detail = null),
            LumoError.Api(ErrorCode.SOURCE_NOT_READY, detail = null),
            LumoError.UnknownCode("SOMETHING_NEW"),
            LumoError.Unreadable(status = 502, cause = null),
        )

        failures.forEach { error ->
            assertThat(repository.onFailureNaming(a.key, error)).isFalse()
        }
        assertThat(repository.state.value.selectedSourceId).isEqualTo(a.key)
        assertThat(store.entries).containsExactly("user-1", a.key)
    }

    // ---- a deletion, seen from this device -----------------------------------

    @Test
    fun `a list without the active source selects the only one left`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val repository = repository()

        // Deleted from the website. This device learns it from its next list.
        api.sources = listOf(b)
        repository.refresh()

        assertThat(repository.state.value.selectedSourceId).isEqualTo(b.key)
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    @Test
    fun `SOURCE_NOT_FOUND on the active source asks when several are left`() = runTest {
        api.sources = listOf(a, b, c)
        store.entries["user-1"] = a.key
        val repository = repository()

        api.sources = listOf(b, c)
        val handled = repository.onFailureNaming(
            a.key,
            LumoError.Api(ErrorCode.SOURCE_NOT_FOUND, detail = null),
        )

        assertThat(handled).isTrue()
        assertThat(repository.state.value).isEqualTo(ActiveSourceState.NeedsChoice(listOf(b, c)))
        // Nothing preselected, and nothing left behind to be found at next launch.
        assertThat(store.entries).isEmpty()
    }

    @Test
    fun `SOURCE_NOT_FOUND with none left leads to adding a source`() = runTest {
        api.sources = listOf(a)
        val repository = repository()

        api.sources = emptyList()
        repository.onSourceGone(a.key)

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.None)
        assertThat(store.entries).isEmpty()
    }

    @Test
    fun `SOURCE_NOT_FOUND is believed even when the list cannot be read`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val repository = repository()

        // The `404` arrived, then the network went. The proof stands on its own,
        // and what was already known says what is left.
        api.failing = true
        repository.onSourceGone(a.key)

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(b)))
        assertThat(store.entries).containsExactly("user-1", b.key)
    }

    // ---- adding a source (US-024) ---------------------------------------------

    @Test
    fun `the first source added becomes active, and the next one does not steal it`() = runTest {
        val repository = repository()
        assertThat(repository.state.value).isEqualTo(ActiveSourceState.None)

        api.sources = listOf(a)
        repository.refresh()
        assertThat(repository.state.value.selectedSourceId).isEqualTo(a.key)

        // Listed first on purpose: "the first of the list" would now answer B.
        api.sources = listOf(b, a)
        repository.refresh()
        assertThat(repository.state.value.selectedSourceId).isEqualTo(a.key)
    }

    @Test
    fun `selecting a source added a moment ago checks it against a fresh list`() = runTest {
        api.sources = listOf(a)
        val repository = repository()

        api.sources = listOf(a, b)
        repository.select(b.key)

        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(a, b)))
    }

    @Test
    fun `selecting a source that does not exist costs nothing`() = runTest {
        api.sources = listOf(a, b)
        store.entries["user-1"] = a.key
        val repository = repository()

        repository.select(c.key)

        assertThat(repository.state.value.selectedSourceId).isEqualTo(a.key)
        assertThat(store.entries).containsExactly("user-1", a.key)
    }

    // ---- two people, one television -------------------------------------------

    @Test
    fun `two accounts on one device do not share a choice`() = runTest {
        api.sources = listOf(a, b)
        val repository = repository()
        repository.select(b.key)

        sessions.clear()
        runCurrent()
        // Signed out is "nothing known", never the previous account's answer.
        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Loading)

        sessions.save(tokens("user-2"))
        runCurrent()
        // The second account has never chosen on this device, so it is asked —
        // it does not inherit B.
        assertThat(repository.state.value).isEqualTo(ActiveSourceState.NeedsChoice(listOf(a, b)))

        repository.select(a.key)
        assertThat(store.entries).containsExactly("user-1", b.key, "user-2", a.key)

        // And signing out wiped nothing: the first account finds its own choice.
        sessions.clear()
        runCurrent()
        sessions.save(tokens("user-1"))
        runCurrent()
        assertThat(repository.state.value.selectedSourceId).isEqualTo(b.key)
    }

    @Test
    fun `a failed fetch for a new account never shows the previous account's source`() = runTest {
        api.sources = listOf(a, b)
        val repository = repository()
        repository.select(b.key)

        api.failing = true
        sessions.save(tokens("user-2"))
        runCurrent()

        // "Keep what was resolved" is scoped to the account it was resolved for.
        assertThat(repository.state.value).isEqualTo(ActiveSourceState.Unavailable)
    }

    @Test
    fun `a token rotation does not re-decide anything`() = runTest {
        api.sources = listOf(a)
        repository()
        val callsBefore = api.calls

        sessions.save(tokens("user-1", access = "rotated"))
        sessions.save(tokens("user-1", access = "rotated again"))
        runCurrent()

        assertThat(api.calls).isEqualTo(callsBefore)
    }

    // ---- helpers -----------------------------------------------------------

    /** A repository that has already reacted to the session, like one a screen meets. */
    private fun TestScope.repository(): ActiveSourceRepository {
        val calls = ApiCaller(ProblemReader(Serializer.moshiBuilder.build()))
        val repository = DefaultActiveSourceRepository(
            session = SessionManager(sessions, RejectingRefresher),
            sources = SourceRepository(api, calls),
            store = store,
            scope = backgroundScope,
        )
        runCurrent()
        return repository
    }

    private fun tokens(userId: String, access: String = "access") = SessionTokens(
        accessToken = access,
        refreshToken = "refresh",
        accessTokenExpiresAt = 0L,
        userId = userId,
        deviceId = "device",
    )
}
