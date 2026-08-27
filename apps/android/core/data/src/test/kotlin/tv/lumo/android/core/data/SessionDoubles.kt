package tv.lumo.android.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.TokenRefreshResult
import tv.lumo.android.core.auth.TokenRefresher
import tv.lumo.android.core.auth.store.SessionStore

/**
 * The session doubles both test classes in this module build on.
 *
 * In one file rather than one per test, because two file-private copies of the
 * same name in the same package do not compile — and because a second, subtly
 * different fake store is how two tests come to disagree about what "signed in"
 * means.
 */

/** The store `SessionManager` is built on, in memory, remembering the last write. */
internal class FakeSessionStore(initial: SessionTokens? = null) : SessionStore {
    private val state = MutableStateFlow(initial)

    /** What was last written, or null if nothing was — the assertion of a sign-in. */
    val saved: SessionTokens? get() = state.value

    override val sessions: Flow<SessionTokens?> = state

    override suspend fun current(): SessionTokens? = state.value

    override suspend fun save(tokens: SessionTokens) {
        state.value = tokens
    }

    override suspend fun clear() {
        state.value = null
    }
}

/**
 * Never called: nothing in these tests provokes a refresh.
 *
 * Rejecting rather than returning a fixture, so a test that unexpectedly *does*
 * refresh fails visibly — with a cleared session — instead of quietly passing on
 * a token nobody meant to issue.
 */
internal object RejectingRefresher : TokenRefresher {
    override suspend fun refresh(refreshToken: String): TokenRefreshResult =
        TokenRefreshResult.Rejected
}
