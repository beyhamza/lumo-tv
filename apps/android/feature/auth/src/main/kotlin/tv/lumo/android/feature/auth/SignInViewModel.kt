package tv.lumo.android.feature.auth

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
 * What the sign-in screen shows and what it is doing.
 *
 * <h2>There is no "signed in" state, and that is the design</h2>
 *
 * Nothing here navigates. `AppStartDecision` watches the session, the shell
 * rebuilds its graph the moment one appears, and the user arrives at the
 * catalogue — or at the source form, if the account has none — without this
 * screen knowing either exists.
 *
 * The alternative would be a success callback and a `navigate()` from here, which
 * is the same journey written twice: once for sign-in, once for registration,
 * once for Google, once for the television's device code. All four end in a
 * session, and the session is what the applications already listen to.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, failure = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, failure = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            when (val result = auth.signIn(current.email, current.password)) {
                is LumoResult.Success ->
                    // Deliberately nothing. The session now exists, and the shell
                    // is already listening; leaving `submitting` true keeps the
                    // button disabled for the frame or two before this screen is
                    // taken away, instead of flashing an enabled form.
                    Unit

                is LumoResult.Failure ->
                    _state.update {
                        it.copy(submitting = false, failure = result.error.asFailure())
                    }
            }
        }
    }
}

data class SignInState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val failure: SignInFailure? = null,
) {
    /**
     * Enough to be worth a round trip, and nothing more.
     *
     * No email regex and no password rules: the server owns both, and a second
     * copy on the device is one more place to forget when they change — and the
     * copy that is wrong. What is checked is the empty form, because sending an
     * obviously blank request to be told what we already know is a round trip for
     * nothing.
     */
    val canSubmit: Boolean
        get() = !submitting && email.isNotBlank() && password.isNotEmpty()
}

/**
 * The four things this screen can say, and why there are exactly four.
 *
 * A sealed set rather than a translated string on the state: the wording belongs
 * to the composable, which has the resources and the locale, and a ViewModel that
 * formatted messages would need a `Context` to do it.
 */
sealed interface SignInFailure {

    /** Wrong email, wrong password, or no such account — indistinguishable. */
    data object InvalidCredentials : SignInFailure

    /**
     * Too many attempts. [seconds] is the server's `Retry-After` when it sent one.
     *
     * This is a wait, not a fault, and the difference is the whole point of
     * carrying the header: a screen that says "something went wrong" invites the
     * user to try again immediately and be refused again.
     */
    data class TooManyAttempts(val seconds: Int?) : SignInFailure

    /**
     * The plan does not allow another device.
     *
     * The contract asks a client to name the ceiling and offer both ways out.
     * The number is not available here and cannot be: reading
     * `Entitlement.max_devices` needs a session, and the whole point of this
     * failure is that none was issued. So the screen names the limit without a
     * figure and points at the two exits.
     */
    data object DeviceLimitReached : SignInFailure

    /** The request never reached the server. */
    data object Offline : SignInFailure

    /** Anything else, including a code newer than this build. */
    data object Unexpected : SignInFailure
}

/**
 * The contract's codes, mapped to what this screen can say.
 *
 * Note the `else`, and note that it is not laziness: the contract states that new
 * codes may appear within v1, and a `when` that did not have one would stop
 * compiling on the next regeneration — or, worse, would have been written with a
 * branch nobody thought about.
 */
private fun LumoError.asFailure(): SignInFailure = when (this) {
    is LumoError.Offline -> SignInFailure.Offline
    is LumoError.Api -> when (code) {
        ErrorCode.INVALID_CREDENTIALS -> SignInFailure.InvalidCredentials
        ErrorCode.RATE_LIMITED -> SignInFailure.TooManyAttempts(retryAfterSeconds)
        ErrorCode.DEVICE_LIMIT_REACHED -> SignInFailure.DeviceLimitReached
        else -> SignInFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> SignInFailure.Unexpected
}
