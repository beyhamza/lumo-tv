package tv.lumo.android.core.auth

/**
 * The transport half of a refresh, seen from `core:auth`.
 *
 * `core:auth` owns the rules — one refresh in flight, rotation, what a rejection
 * means — but has no business knowing about Retrofit. `core:network` implements
 * this against the generated `AuthApi`. The dependency therefore points one way
 * only (network → auth), which is what keeps the two modules from becoming one.
 */
interface TokenRefresher {
    suspend fun refresh(refreshToken: String): TokenRefreshResult
}

sealed interface TokenRefreshResult {

    /** The server rotated the pair. The old refresh token is now spent. */
    data class Rotated(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Int,
    ) : TokenRefreshResult

    /**
     * The server refused the refresh token: expired, revoked, or already used.
     *
     * Reuse means the whole device chain has just been revoked server-side
     * (docs/domain-model.md, `refresh_token`). Either way this device has no
     * session any more and must sign in again. Retrying is pointless and, in the
     * reuse case, actively harmful.
     */
    data object Rejected : TokenRefreshResult

    /**
     * The refresh could not be attempted — no network, timeout, 5xx.
     *
     * Distinct from [Rejected] on purpose: signing a user out because their
     * train went into a tunnel is the failure mode this distinction exists to
     * prevent.
     */
    data class Unavailable(val cause: Throwable? = null) : TokenRefreshResult
}
