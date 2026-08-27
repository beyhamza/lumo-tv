package tv.lumo.android.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AuthRepository
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * "Continue with Google", from the button to the session (US-03).
 *
 * <h2>Its own ViewModel, deliberately</h2>
 *
 * Signing in and creating an account are two screens and two forms, but Google is
 * the same three steps on both — and on a first SSO the two are not even
 * distinguishable: the account is created if it does not exist and linked if it
 * does, and the server decides which. One ViewModel behind one reusable button
 * rather than the same state duplicated into [SignInViewModel] and
 * [SignUpViewModel], where it would drift.
 *
 * <h2>Nothing navigates here either</h2>
 *
 * Same as every other road to a session: the repository opens it,
 * `AppStartDecision` notices, and the shell rebuilds its graph. This screen never
 * learns what came next.
 *
 * <h2>The `Activity` is passed, never held</h2>
 *
 * The account picker opens a window over the current one and needs the activity it
 * sits on. It arrives as an argument to [signIn] and is gone when the call
 * returns; a ViewModel that stored one would outlive it and leak it across a
 * rotation.
 */
@HiltViewModel
class GoogleSignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val google: GoogleIdRetriever,
) : ViewModel() {

    /**
     * Whether to draw the button at all.
     *
     * Read once: it is a build-time fact, not a state. False in a checkout with no
     * OAuth client configured, and the screens then show their email form alone.
     */
    val available: Boolean = google.configured

    private val _state = MutableStateFlow(GoogleSignInState())
    val state: StateFlow<GoogleSignInState> = _state.asStateFlow()

    fun signIn(activity: Activity) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            when (val outcome = google.retrieve(activity)) {
                is GoogleIdOutcome.Token -> exchange(outcome.idToken)

                // Closing the sheet is a decision, not a fault. The form comes
                // back exactly as it was, with nothing red on it.
                GoogleIdOutcome.Dismissed ->
                    _state.update { it.copy(submitting = false, failure = null) }

                GoogleIdOutcome.NoAccount ->
                    _state.update {
                        it.copy(submitting = false, failure = GoogleSignInFailure.NoAccount)
                    }

                GoogleIdOutcome.Unavailable ->
                    _state.update {
                        it.copy(submitting = false, failure = GoogleSignInFailure.Unavailable)
                    }
            }
        }
    }

    private suspend fun exchange(idToken: String) {
        when (val result = auth.signInWithGoogle(idToken)) {
            is LumoResult.Success ->
                // Nothing, and `submitting` stays true. The session exists, the
                // shell is already listening, and re-enabling a button for the
                // frame before this screen disappears would only invite a second
                // press.
                Unit

            is LumoResult.Failure ->
                _state.update {
                    it.copy(submitting = false, failure = result.error.asGoogleFailure())
                }
        }
    }
}

data class GoogleSignInState(
    val submitting: Boolean = false,
    val failure: GoogleSignInFailure? = null,
)

/**
 * What can go wrong, and it is two different halves.
 *
 * [NoAccount] and [Unavailable] happen on the device, before anything is sent.
 * The rest are the server's answers to a token it verified itself. They are one
 * type because the button shows one message, but the distinction is why
 * [Unavailable] does not say "try again" — nothing will have changed.
 */
sealed interface GoogleSignInFailure {

    /** No Google account on this device. */
    data object NoAccount : GoogleSignInFailure

    /** The picker could not run: no provider, no Play services, or a fault in it. */
    data object Unavailable : GoogleSignInFailure

    /**
     * The server refused the token (`OAUTH_TOKEN_INVALID`).
     *
     * Which is not something the user did: it means a token that failed
     * verification — most often an application configured with the Android OAuth
     * client ID instead of the web one, so the `aud` claim does not match what the
     * server expects. The message says the sign-in could not be completed and does
     * not blame the account.
     */
    data object Rejected : GoogleSignInFailure

    /** Too many attempts. [seconds] is the server's `Retry-After` when it sent one. */
    data class TooManyAttempts(val seconds: Int?) : GoogleSignInFailure

    /** The plan does not allow another device — same ceiling as an email sign-in. */
    data object DeviceLimitReached : GoogleSignInFailure

    /** The request never left the device. */
    data object Offline : GoogleSignInFailure

    /** Anything else, including a code newer than this build. */
    data object Unexpected : GoogleSignInFailure
}

/**
 * The contract's codes, mapped to what this button can say.
 *
 * Internal rather than private so it can be tested without a device: it is the
 * only part of this flow that is pure, and it is the part that goes wrong when a
 * code is added to the contract.
 *
 * The `else` is deliberate, not lazy. The contract states that new codes may
 * appear within v1, and an exhaustive `when` would either stop compiling on the
 * next regeneration or have been written with a branch nobody thought about.
 */
internal fun LumoError.asGoogleFailure(): GoogleSignInFailure = when (this) {
    is LumoError.Offline -> GoogleSignInFailure.Offline
    is LumoError.Api -> when (code) {
        ErrorCode.OAUTH_TOKEN_INVALID -> GoogleSignInFailure.Rejected
        ErrorCode.RATE_LIMITED -> GoogleSignInFailure.TooManyAttempts(retryAfterSeconds)
        ErrorCode.DEVICE_LIMIT_REACHED -> GoogleSignInFailure.DeviceLimitReached
        else -> GoogleSignInFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> GoogleSignInFailure.Unexpected
}
