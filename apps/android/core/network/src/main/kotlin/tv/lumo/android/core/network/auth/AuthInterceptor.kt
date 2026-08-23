package tv.lumo.android.core.network.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tv.lumo.android.core.auth.SessionManager

/**
 * Attaches `Authorization: Bearer <access_token>` to every call that needs one.
 *
 * Public endpoints are skipped by path rather than by opting each call in: the
 * contract marks the `/auth` endpoints public (docs/domain-model.md §3), and a bearer token
 * on a login request is at best noise and at worst a token in a server log we do
 * not control.
 *
 * `runBlocking` is correct here. OkHttp interceptors are blocking by contract
 * and run on OkHttp's own thread pool, never on the main thread; suspending is
 * not an option the interface offers. The read it blocks on is a DataStore
 * lookup that is already in memory after the first call.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isPublic()) return chain.proceed(request)

        val accessToken = runBlocking { sessionManager.currentAccessToken() }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                // Remembering which token was used is what lets
                // TokenAuthenticator tell "my token expired" from "someone else
                // already refreshed while my request was in flight".
                .tag(SentWithToken::class, SentWithToken(accessToken))
                .build(),
        )
    }
}

/** The access token a request went out with. Never logged. */
internal data class SentWithToken(val accessToken: String)

internal fun Request.isPublic(): Boolean {
    val path = url.encodedPath
    return PUBLIC_PATH_SEGMENTS.any { path.contains(it) }
}

// `/auth/logout` is deliberately absent: it is authenticated, and it is the one
// auth endpoint that must carry a bearer token.
private val PUBLIC_PATH_SEGMENTS = listOf(
    "/auth/register",
    "/auth/login",
    "/auth/refresh",
    "/auth/oauth/",
    "/auth/verify-email",
    "/auth/password/",
    "/auth/device/code",
    "/auth/device/token",
)
