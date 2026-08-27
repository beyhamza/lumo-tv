package tv.lumo.android.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.valueOrNull

/**
 * The session, seen from the account screen — and the way out of it (US-04).
 *
 * <h2>Why signing out lives here</h2>
 *
 * US-04's second scenario ends "l'appareil doit se reconnecter", and until this
 * existed there was no way to get back to that state: a device that signed in
 * once stayed signed in until the application was uninstalled. That makes the
 * story impossible to demonstrate twice and impossible to qualify at all
 * (`R-17`, `R-18`), which is what S2-07 is for.
 *
 * <h2>Nothing here navigates either</h2>
 *
 * Signing out drops the session, `AppStartDecision` sees it go, and the shell
 * rebuilds its graph onto the way in — with the back stack cleared, which is the
 * part that matters: `BACK` must not walk back into an account that is gone.
 *
 * <h2>The address is shown because "still signed in" has to be visible</h2>
 *
 * "Kill the application and reopen it connected" is not observable if the screen
 * only says *signed in*: a screen that always says that is indistinguishable
 * from one that is right. The account's own address is the cheapest thing that
 * could not have been guessed.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
) : ViewModel() {

    private val email = MutableStateFlow<String?>(null)
    private val signingOut = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(account.isSignedIn, email, signingOut) { signedIn, address, out ->
            SettingsUiState(signedIn = signedIn, email = address, signingOut = out)
        }.stateIn(
            scope = viewModelScope,
            // Survives a rotation and a brief trip to the background without
            // re-reading the encrypted store, and stops collecting when the
            // screen is really gone.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(),
        )

    init {
        viewModelScope.launch {
            account.isSignedIn.distinctUntilChanged().collect { signedIn ->
                // Asked once per session rather than on every emission: the
                // session flow re-emits on each token rotation, and re-reading
                // an address that cannot have changed would put a request on the
                // wire every hour for nothing.
                email.value = if (signedIn) account.me().valueOrNull()?.email else null
            }
        }
    }

    /**
     * Ends the session on this device.
     *
     * The server is told first so the seat on the plan is freed, but the local
     * session goes either way — that is [AccountRepository]'s doing and it is
     * deliberate: someone who taps "sign out" on a train and stays signed in has
     * been lied to by the application, and the refresh token they are still
     * holding is the thing that made the tap urgent.
     */
    fun signOut() {
        if (signingOut.value) return
        signingOut.value = true

        viewModelScope.launch {
            account.signOut()
            // Usually this screen is gone by now — the graph is rebuilt the
            // moment the session disappears. Reset anyway rather than leave a
            // spinner behind if it is not.
            signingOut.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the settings surfaces render. Deliberately smaller than it will end up. */
data class SettingsUiState(
    val signedIn: Boolean = false,
    /** The account's address, once it has been read. Null while unknown. */
    val email: String? = null,
    val signingOut: Boolean = false,
)

/**
 * Maps the state to a string resource, once, for both surfaces.
 *
 * The mapping is here rather than in each screen for the same reason the view
 * model is shared: a phone and a television that disagree about what "signed in"
 * looks like is a bug waiting for a translator to find it.
 */
@StringRes
internal fun sessionLabelOf(state: SettingsUiState): Int =
    if (state.signedIn) R.string.feature_settings_session_open else R.string.feature_settings_session_none
