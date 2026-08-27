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
import tv.lumo.android.network.generated.model.Locale

/**
 * Creating an account (US-01).
 *
 * Ends the same way signing in does: the session appears, the shell rebuilds its
 * graph, and nothing here navigates. Registration signs the user in immediately —
 * the contract says so — so there is no "check your email to continue" wall. The
 * verification email is sent and blocks nothing.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpState())
    val state: StateFlow<SignUpState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, failure = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, failure = null) }

    fun onDisplayNameChange(value: String) =
        _state.update { it.copy(displayName = value, failure = null) }

    /** @param locale what the interface is currently displaying in. */
    fun submit(locale: Locale) {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            val result = auth.register(
                email = current.email,
                password = current.password,
                displayName = current.displayName,
                locale = locale,
            )

            when (result) {
                // Nothing on success, for the same reason as signing in: the
                // session exists and the shell is already listening.
                is LumoResult.Success -> Unit

                is LumoResult.Failure ->
                    _state.update {
                        it.copy(submitting = false, failure = result.error.asFailure())
                    }
            }
        }
    }
}

data class SignUpState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val submitting: Boolean = false,
    val failure: SignUpFailure? = null,
) {
    /**
     * Whether the password meets the one rule stated up front.
     *
     * Null before anything is typed, so an empty field is not shown as an error
     * on arrival — a form that greets someone in red is a form that reads as
     * broken.
     */
    val passwordTooShort: Boolean?
        get() = if (password.isEmpty()) null else password.length < MIN_PASSWORD_LENGTH

    /**
     * US-01 is specific: the button stays disabled and the unmet rule is shown
     * **before** submission, not after.
     *
     * The rule is the server's — it re-checks and answers `PASSWORD_TOO_WEAK` —
     * and this is a courtesy, never the validation.
     */
    val canSubmit: Boolean
        get() = !submitting &&
            email.isNotBlank() &&
            password.length >= MIN_PASSWORD_LENGTH

    companion object {
        /**
         * What the contract accepts and what `AccountService` enforces.
         *
         * The contract's description of `password` also says strength is measured
         * by entropy (zxcvbn) rather than by composition rules. **Nothing in the
         * product does that yet** — not the server, whose check is this same
         * length, and not the site, which shows this same rule. Measuring entropy
         * here and nowhere else would make the phone refuse passwords the server
         * would happily accept, which is the worst of the three possible states.
         * Written up in `docs/design/api-gaps.md`.
         */
        const val MIN_PASSWORD_LENGTH = 10
    }
}

/** What this screen can say, and nothing it cannot. */
sealed interface SignUpFailure {

    /**
     * An account may already exist for this address.
     *
     * The wording invites signing in or resetting a password **without asserting
     * that the address is registered**. The contract's own protection is about
     * timing — the response must take the same time either way — and the message
     * is where a client can avoid saying out loud what the timing was hidden to
     * protect.
     */
    data object EmailMayExist : SignUpFailure

    /** The server refused the password. */
    data object PasswordTooWeak : SignUpFailure

    /** A field the server rejected for a reason of its own. */
    data object Invalid : SignUpFailure

    /** Too many attempts, with the server's own delay when it sent one. */
    data class TooManyAttempts(val seconds: Int?) : SignUpFailure

    /** The request never reached the server. */
    data object Offline : SignUpFailure

    /** Anything else, including a code newer than this build. */
    data object Unexpected : SignUpFailure
}

private fun LumoError.asFailure(): SignUpFailure = when (this) {
    is LumoError.Offline -> SignUpFailure.Offline
    is LumoError.Api -> when (code) {
        ErrorCode.EMAIL_ALREADY_REGISTERED -> SignUpFailure.EmailMayExist
        ErrorCode.PASSWORD_TOO_WEAK -> SignUpFailure.PasswordTooWeak
        ErrorCode.VALIDATION_FAILED -> SignUpFailure.Invalid
        ErrorCode.RATE_LIMITED -> SignUpFailure.TooManyAttempts(retryAfterSeconds)
        // The `else` is required rather than tidy: new codes may appear within
        // v1, and this is the branch that keeps an older application working
        // against a newer server.
        else -> SignUpFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> SignUpFailure.Unexpected
}
