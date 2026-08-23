package tv.lumo.android.core.network.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import tv.lumo.android.core.auth.RefreshOutcome
import tv.lumo.android.core.auth.SessionManager

/**
 * Turns a 401 into a refresh and one replay (US-04, "Rafraîchissement
 * transparent").
 *
 * An [Authenticator] rather than an interceptor that retries: OkHttp calls this
 * only after a 401, gives us the failed response, and counts the retries itself.
 *
 * The single-refresh guarantee is NOT here — it is in
 * [SessionManager.refresh], which is the only thing that can enforce it across
 * calls that are already in flight. This class's job is to hand that method the
 * token the failed request used, so a caller that lost the race replays with the
 * refreshed token instead of asking for a second rotation.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // One replay. If the refreshed token also comes back 401, the problem is
        // not the token, and retrying forever would turn one failure into a
        // request loop against the API.
        if (response.priorResponseCount() >= 1) return null

        // Refresh itself is unauthenticated; a 401 there means the refresh token
        // is gone, which SessionManager has already acted on.
        if (response.request.isPublic()) return null

        val sentWith = response.request.tag(SentWithToken::class)?.accessToken

        return when (val outcome = runBlocking { sessionManager.refresh(sentWith) }) {
            is RefreshOutcome.Refreshed -> response.request.newBuilder()
                .header("Authorization", "Bearer ${outcome.tokens.accessToken}")
                .tag(SentWithToken::class, SentWithToken(outcome.tokens.accessToken))
                .build()

            // No session left, or no network to get one. Give up and let the
            // 401 surface: the UI observes SessionManager.isSignedIn and sends
            // the user back to sign-in.
            RefreshOutcome.SignedOut -> null
            is RefreshOutcome.Unavailable -> null
        }
    }
}

private fun Response.priorResponseCount(): Int {
    var count = 0
    var prior = priorResponse
    while (prior != null) {
        count++
        prior = prior.priorResponse
    }
    return count
}
