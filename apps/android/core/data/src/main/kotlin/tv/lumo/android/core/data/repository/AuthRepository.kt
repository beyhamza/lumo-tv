package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.DeviceDescriber
import tv.lumo.android.core.data.map
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.model.AuthSession
import tv.lumo.android.network.generated.model.DeviceCodeRequest
import tv.lumo.android.network.generated.model.DeviceCodeResponse
import tv.lumo.android.network.generated.model.DeviceTokenRequest
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.GoogleSignInRequest
import tv.lumo.android.network.generated.model.Locale
import tv.lumo.android.network.generated.model.LoginRequest
import tv.lumo.android.network.generated.model.RegisterRequest

/**
 * Getting a session, and being the only thing that opens one.
 *
 * <h2>The session is stored here, not by the screen</h2>
 *
 * A sign-in screen that received tokens and saved them itself would be a second
 * place that knows how a session is persisted — and there are four roads to it:
 * sign-in, registration (S2-04), Google (S2-06) and the television's device code
 * (S2-12) all end in exactly the same way. They end here instead.
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

    /**
     * Creates an account and opens its session (US-01).
     *
     * Registration signs the user in immediately — the contract says so, and it
     * is why this ends exactly like [signIn] rather than returning to a sign-in
     * form. A verification email goes out at the same time and blocks nothing:
     * an account that cannot be used until a link is clicked is an account that
     * is abandoned on a train.
     *
     * **The password is not validated here.** The minimum is the server's, it is
     * re-checked there, and a copy in this layer would be a second rule to keep
     * in step. The screen shows it before submission because US-01 asks for
     * that — as a courtesy, never as the validation.
     *
     * @param locale what the interface is currently displaying in. Sent rather
     * than left out: the contract falls back to `Accept-Language` and then to
     * English, and this client sends no such header, so a French user would be
     * written down as English and get English email.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
        locale: Locale,
    ): LumoResult<Unit> {
        val result = calls.call {
            api.register(
                RegisterRequest(
                    email = email.trim(),
                    password = password,
                    device = device.registration(),
                    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                    locale = locale,
                ),
            )
        }

        return result.map { session.open(it.asTokens()) }
    }

    /**
     * Exchanges a Google ID token for a session (US-03).
     *
     * **The client sends the token and nothing else â never an email.** The
     * server verifies the signature, `aud`, `iss` and `exp` itself and reads the
     * address out of the verified token; an email supplied by a client is an
     * email anybody can supply.
     *
     * Attaching a Google identity to an account that already exists under the
     * same address is the server's job too, and it is why this returns a session
     * rather than "created" or "linked": the contract says a duplicate account is
     * never created, and a screen that had to tell the two apart would be a
     * screen deciding which account somebody owns.
     *
     * @param idToken what Credential Manager handed back. Not logged, not
     * stored, not kept after this call.
     */
    suspend fun signInWithGoogle(idToken: String): LumoResult<Unit> {
        val result = calls.call {
            api.signInWithGoogle(
                GoogleSignInRequest(idToken = idToken, device = device.registration()),
            )
        }

        return result.map { session.open(it.asTokens()) }
    }

    /**
     * Asks for a code the television can show (US-05, RFC 8628).
     *
     * The whole flow exists so that nobody types an email and a password on a
     * remote control — the first place people give up on a TV application. The
     * `device_code` in the answer is the television's secret and is polled with;
     * the `user_code` is the short one on screen.
     */
    suspend fun requestDeviceCode(): LumoResult<DeviceCodeResponse> {
        val registration = device.registration()

        return calls.call {
            api.requestDeviceCode(
                DeviceCodeRequest(
                    platform = registration.platform,
                    name = registration.name,
                    model = registration.model,
                    appVersion = registration.appVersion,
                ),
            )
        }
    }

    /**
     * One poll, translated into what the television should do next.
     *
     * **`AUTHORIZATION_PENDING` is the nominal answer**, not a failure. It is what
     * comes back for every poll until somebody approves on their phone, and a
     * client that showed it as an error would put a red message on a screen where
     * nothing is wrong — which is most of the time this screen is on display.
     *
     * The session is opened here on approval, exactly as for a sign-in: this is
     * the only place a session is opened, and the television's activation is the
     * fourth road that ends here.
     */
    suspend fun pollDeviceToken(deviceCode: String): DevicePoll {
        val result = calls.call {
            api.pollDeviceToken(DeviceTokenRequest(deviceCode = deviceCode))
        }

        return when (result) {
            is LumoResult.Success -> {
                session.open(result.value.asTokens())
                DevicePoll.Approved
            }

            is LumoResult.Failure -> result.error.asPoll()
        }
    }
}

/**
 * What one poll means, in RFC 8628's vocabulary.
 *
 * A type of its own rather than a raw [LumoError] because three of these are not
 * errors at all: two say "keep going" and one says "start over". Handing a screen
 * an error object and trusting it to know which is which is how a pending
 * authorization ends up rendered in red.
 */
sealed interface DevicePoll {

    /** Approved. The session is already open; nothing else to do. */
    data object Approved : DevicePoll

    /** Nobody has approved yet. Poll again at the same interval. */
    data object Pending : DevicePoll

    /** Polling too fast. RFC 8628 says add five seconds, and keep going. */
    data object SlowDown : DevicePoll

    /** The user refused on their phone. Stop, and say so. */
    data object Denied : DevicePoll

    /**
     * The code is no longer usable — expired, or unknown to the server.
     *
     * The television asks for a new one and shows it **without anyone doing
     * anything**: the person who walked away to fetch their phone is not there to
     * press a button, and a screen showing a dead code is a screen that has
     * stopped working without saying so.
     */
    data object NeedsNewCode : DevicePoll

    /** Something else. The screen keeps its code and waits. */
    data class Unavailable(val error: LumoError) : DevicePoll
}

private fun LumoError.asPoll(): DevicePoll = when (this) {
    // A poll that never left the device is not a refusal. The code on screen is
    // still valid, and the next attempt may well succeed.
    is LumoError.Offline -> DevicePoll.Unavailable(this)
    is LumoError.Api -> when (code) {
        ErrorCode.AUTHORIZATION_PENDING -> DevicePoll.Pending
        ErrorCode.SLOW_DOWN -> DevicePoll.SlowDown
        ErrorCode.ACCESS_DENIED -> DevicePoll.Denied
        ErrorCode.EXPIRED_TOKEN, ErrorCode.DEVICE_CODE_NOT_FOUND -> DevicePoll.NeedsNewCode
        else -> DevicePoll.Unavailable(this)
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> DevicePoll.Unavailable(this)
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
