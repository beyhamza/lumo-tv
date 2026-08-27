package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.DeviceDescriber
import tv.lumo.android.core.data.map
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.model.AuthSession
import tv.lumo.android.network.generated.model.LoginRequest

/**
 * Getting a session, and being the only thing that opens one.
 *
 * <h2>The session is stored here, not by the screen</h2>
 *
 * A sign-in screen that received tokens and saved them itself would be a second
 * place that knows how a session is persisted — and there would be three of them
 * within a sprint, since registration (S2-04), Google (S2-06) and the television's
 * device code (S2-12) all end in exactly the same way. They end here instead.
 *
 * The consequence is worth stating because it is what makes the screens simple:
 * **nothing navigates after signing in.** `AppStartDecision` watches the session,
 * the shell rebuilds its graph when the answer changes, and the user arrives on
 * the catalogue or on the source form without the sign-in screen knowing either
 * exists.
 *
 * <h2>Failures are the contract's, unchanged</h2>
 *
 * `INVALID_CREDENTIALS` is deliberately generic on the server — it must not
 * reveal whether the email exists — and it stays generic here. `RATE_LIMITED`
 * arrives with `Retry-After` on [tv.lumo.android.core.data.LumoError.Api], which
 * is what lets the screen show a wait rather than a fault (US-02).
 */
@Singleton
class AuthRepository @Inject internal constructor(
    private val api: AuthApi,
    private val calls: ApiCaller,
    private val session: SessionManager,
    private val device: DeviceDescriber,
) {

    /**
     * Signs in and opens the session.
     *
     * @return the signed-in user on success. The tokens are not returned: there
     * is nothing a caller may legitimately do with them that this has not already
     * done, and handing them out is how a copy ends up somewhere unencrypted.
     */
    suspend fun signIn(email: String, password: String): LumoResult<Unit> {
        val result = calls.call {
            api.login(
                LoginRequest(
                    // Trimmed, because a keyboard's autocomplete adds a trailing
                    // space often enough to matter and the server would reject it
                    // as a wrong password — which is the least helpful of all the
                    // possible answers.
                    email = email.trim(),
                    // Never trimmed. A space is a legal character in a password,
                    // and silently removing one turns a correct password into
                    // INVALID_CREDENTIALS forever.
                    password = password,
                    device = device.registration(),
                ),
            )
        }

        return result.map { session.open(it.asTokens()) }
    }
}

private fun AuthSession.asTokens() = SessionTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    // The server says how long it lasts; this is a hint for the client, and the
    // server remains the one that decides. `expiresIn` is seconds.
    accessTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1_000L,
    userId = user.id.toString(),
    deviceId = deviceId.toString(),
)
