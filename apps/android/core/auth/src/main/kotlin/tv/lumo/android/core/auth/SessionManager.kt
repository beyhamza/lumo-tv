package tv.lumo.android.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    suspend fun signOut() = store.clear()

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
