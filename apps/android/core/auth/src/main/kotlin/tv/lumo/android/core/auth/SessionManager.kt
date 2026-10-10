package tv.lumo.android.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.lumo.android.core.auth.store.SessionStore

/**
 * The device's session, and the only place it is refreshed.
 *
 * **One refresh in flight, ever.** This is the bug the backlog calls out by name
 * (US-04): a screen that fires three requests at startup gets three 401s, each
 * triggers a refresh, and the second refresh presents a token the first one has
 * already rotated. The server sees a consumed refresh token, correctly concludes
 * the token was stolen, and revokes the entire device chain — signing the user
 * out for doing nothing but opening the app.
 *
 * Two things prevent it, and both are needed:
 *
 * 1. [refreshMutex] serialises refreshes, so a second caller waits instead of
 *    racing.
 * 2. Inside the lock, the caller's stale access token is compared against what
 *    is stored. If they differ, someone else already rotated while this caller
 *    was waiting, and the right answer is the new token — not a second refresh.
 *
 * Without (2) the mutex only converts a simultaneous double refresh into a
 * sequential one, which fails in exactly the same way, just more reliably.
 */
@Singleton
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val refresher: TokenRefresher,
    /** What else goes with the session; see [SessionEndCleaner]. Empty in most tests. */
    private val cleaners: Set<@JvmSuppressWildcards SessionEndCleaner> = emptySet(),
) {

    private val refreshMutex = Mutex()

    val sessions: Flow<SessionTokens?> = store.sessions

    /** Emits true while a session exists. Drives the navigation start route. */
    val isSignedIn: Flow<Boolean> = sessions.map { it != null }

    suspend fun current(): SessionTokens? = store.current()

    suspend fun currentAccessToken(): String? = store.current()?.accessToken

    /** Called once after a successful sign-in, activation or registration. */
    suspend fun open(tokens: SessionTokens) = store.save(tokens)

    /**
     * Drops the local session. The caller is responsible for telling the server
     * (`POST /auth/logout`) first when it can — but a sign-out must succeed
     * locally even when the network does not.
     */
    suspend fun signOut() {
        store.clear()
        endSession()
    }

    /**
     * Session first, then the account's data: the screens leave for the
     * activation page at once, and the purge is over before [signOut] returns, so
     * nothing signs in on top of a half-cleaned device.
     *
     * Best effort, cleaner by cleaner. A sign-out must succeed locally whatever
     * happens (a full disk, a locked database); one cleaner failing does not
     * spare the others, and the next session's own data replaces what is left.
     *
     * **Not cancellable**, and that is what the device replay of 10 October
     * showed: the sign-out is launched from the settings screen's scope, clearing
     * the session navigates away from that screen, its scope is cancelled — and
     * the purge stopped half-way, the database emptied but the preferences kept.
     */
    private suspend fun endSession() = withContext(NonCancellable) {
        for (cleaner in cleaners) {
            try {
                cleaner.onSessionEnded()
            } catch (_: Exception) {
                // Not logged: what failed to be deleted is the previous account's data.
            }
        }
    }

    /**
     * @param staleAccessToken the token whose request just came back 401, or
     * null to refresh unconditionally. Passing it is what lets a caller that
     * lost the race take the already-refreshed token instead of rotating again.
     */
    suspend fun refresh(staleAccessToken: String?): RefreshOutcome = refreshMutex.withLock {
        val current = store.current() ?: return RefreshOutcome.SignedOut

        if (staleAccessToken != null && current.accessToken != staleAccessToken) {
            // Someone refreshed while this caller waited for the lock.
            return RefreshOutcome.Refreshed(current)
        }

        when (val result = refresher.refresh(current.refreshToken)) {
            is TokenRefreshResult.Rotated -> {
                val rotated = current.copy(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    accessTokenExpiresAt = System.currentTimeMillis() +
                        result.expiresInSeconds * 1_000L,
                )
                store.save(rotated)
                RefreshOutcome.Refreshed(rotated)
            }

            TokenRefreshResult.Rejected -> {
                store.clear()
                endSession()
                RefreshOutcome.SignedOut
            }

            is TokenRefreshResult.Unavailable -> RefreshOutcome.Unavailable(result.cause)
        }
    }
}

sealed interface RefreshOutcome {
    data class Refreshed(val tokens: SessionTokens) : RefreshOutcome

    /** No session left. The UI must send the user back to sign-in. */
    data object SignedOut : RefreshOutcome

    /** Transient. The session is intact; the request may be retried later. */
    data class Unavailable(val cause: Throwable?) : RefreshOutcome
}
