package tv.lumo.android.core.network.auth

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.auth.TokenRefresher
import tv.lumo.android.core.auth.TokenRefreshResult
import tv.lumo.android.core.network.di.Unauthenticated
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.model.RefreshRequest

/**
 * The transport behind [TokenRefresher], implemented against the generated
 * `AuthApi` (ADR 0001 — the client is generated, never hand-written).
 *
 * Injected with the [Unauthenticated] API on purpose: refresh must not carry the
 * expired bearer token and must not be retried by [TokenAuthenticator].
 *
 * The status mapping is the whole point of this class:
 *
 * - **401 / 409** — the refresh token is expired, revoked or already used. Note
 *   what 409 means server-side: reuse was detected and the device's entire chain
 *   was just revoked (docs/domain-model.md, `refresh_token`). There is nothing
 *   left to retry with.
 * - **anything else, including no response at all** — unknown. The session is
 *   kept. Signing a user out because a request timed out is the failure this
 *   distinction exists to prevent.
 */
@Singleton
class RetrofitTokenRefresher @Inject constructor(
    @param:Unauthenticated private val authApi: AuthApi,
) : TokenRefresher {

    override suspend fun refresh(refreshToken: String): TokenRefreshResult = try {
        val response = authApi.refreshSession(RefreshRequest(refreshToken = refreshToken))
        val body = response.body()

        when {
            response.isSuccessful && body != null -> TokenRefreshResult.Rotated(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                expiresInSeconds = body.expiresIn,
            )

            response.code() == 401 || response.code() == 409 -> TokenRefreshResult.Rejected

            else -> TokenRefreshResult.Unavailable(
                IOException("Refresh failed with HTTP ${response.code()}"),
            )
        }
    } catch (e: IOException) {
        TokenRefreshResult.Unavailable(e)
    }
}
