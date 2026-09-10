package tv.lumo.android.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.Source

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
 *
 * <h2>The rest of the screen (M6)</h2>
 *
 * The mobile mock-up adds three counts and one switch on top of the session:
 * how many sources the account has, whether they refresh on their own, how many
 * devices hold a session, and where a television is paired. All of it is read
 * here rather than in the screen so the television renders the same numbers
 * from the same state (AGENTS.md §2).
 *
 * `auto_sync` is a property of each **source**, not of the account — the
 * contract is explicit about why. The switch therefore reads as "every source
 * refreshes on its own" and writes to every source; a mixed account shows the
 * switch off, and turning it on aligns them.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val sources: SourceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val email = MutableStateFlow<String?>(null)
    private val signingOut = MutableStateFlow(false)
    private val details = MutableStateFlow(SettingsDetails())

    val uiState: StateFlow<SettingsUiState> =
        combine(account.isSignedIn, email, signingOut, details) { signedIn, address, out, more ->
            SettingsUiState(
                signedIn = signedIn,
                email = address,
                signingOut = out,
                sourceCount = more.sourceCount,
                autoSync = more.autoSync,
                autoSyncPending = more.autoSyncPending,
                deviceCount = more.deviceCount,
                activationUrl = BuildConfig.ACTIVATION_URL,
                appVersion = more.appVersion,
            )
        }.stateIn(
            scope = viewModelScope,
            // Survives a rotation and a brief trip to the background without
            // re-reading the encrypted store, and stops collecting when the
            // screen is really gone.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(activationUrl = BuildConfig.ACTIVATION_URL),
        )

    init {
        details.update { it.copy(appVersion = appVersion()) }

        viewModelScope.launch {
            account.isSignedIn.distinctUntilChanged().collect { signedIn ->
                // Asked once per session rather than on every emission: the
                // session flow re-emits on each token rotation, and re-reading
                // an address that cannot have changed would put a request on the
                // wire every hour for nothing.
                email.value = if (signedIn) account.me().valueOrNull()?.email else null
                if (signedIn) {
                    loadSources()
                    loadDevices()
                } else {
                    details.update { SettingsDetails(appVersion = it.appVersion) }
                }
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

    /**
     * Turns automatic refresh on or off, for every source of the account.
     *
     * Optimistic: the switch moves at once, and moves back only if a write is
     * refused. `auto_sync` is the one property of `PATCH /sources/{id}` that
     * leaves the catalogue alone, so this never restarts an ingestion.
     */
    fun setAutoSync(enabled: Boolean) {
        val current = details.value
        if (current.autoSyncPending || current.sourceIds.isEmpty()) return

        details.update { it.copy(autoSync = enabled, autoSyncPending = true) }

        viewModelScope.launch {
            val refused = current.sourceIds.any { id ->
                sources.update(id, autoSync = enabled) is LumoResult.Failure
            }
            details.update { it.copy(autoSyncPending = false) }
            // Re-read rather than assume: on a partial failure the honest value
            // is whatever the server now holds, not the one that was asked for.
            if (refused) loadSources()
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            val list = sources.sources().valueOrNull() ?: return@launch
            details.update {
                it.copy(
                    sourceCount = list.size,
                    sourceIds = list.map { source -> source.id.toString() },
                    autoSync = autoSyncOf(list),
                )
            }
        }
    }

    private fun loadDevices() {
        viewModelScope.launch {
            val list = account.devices().valueOrNull() ?: return@launch
            details.update { it.copy(deviceCount = list.size) }
        }
    }

    /**
     * Read from the package manager rather than from `BuildConfig`, for the
     * reason `core:data`'s device describer gives: this module is shared by two
     * applications with two version numbers, and the package manager answers for
     * the one actually running.
     */
    private fun appVersion(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (missing: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What is read after the session, kept apart so a sign-out drops it in one move. */
private data class SettingsDetails(
    val sourceCount: Int? = null,
    val sourceIds: List<String> = emptyList(),
    val autoSync: Boolean? = null,
    val autoSyncPending: Boolean = false,
    val deviceCount: Int? = null,
    val appVersion: String? = null,
)

/** What the settings surfaces render. Deliberately smaller than it will end up. */
data class SettingsUiState(
    val signedIn: Boolean = false,
    /** The account's address, once it has been read. Null while unknown. */
    val email: String? = null,
    val signingOut: Boolean = false,

    // ---- M6: what the mobile mock-up adds around the session ----------------

    /** How many sources the account holds. Null until read, or when the read failed. */
    val sourceCount: Int? = null,
    /**
     * Whether every source refreshes on its own. Null when there is no source
     * to ask, or while the list has not been read yet.
     */
    val autoSync: Boolean? = null,
    /** A write to `auto_sync` is in flight; the switch waits for it. */
    val autoSyncPending: Boolean = false,
    /** How many devices hold a session on this account, this one included. */
    val deviceCount: Int? = null,
    /** Where a television is paired from a phone — `LUMO_ACTIVATION_URL`. */
    val activationUrl: String = "",
    /** `versionName` of the running application. Null only if the package is unreadable. */
    val appVersion: String? = null,
) {

    /** The letter in the account disc: the address's first character, or nothing. */
    val initial: String
        get() = email?.trim()?.firstOrNull()?.uppercase().orEmpty()

    /** `lumo.tv/activate`, as the mock-up prints it — the URL without its scheme. */
    val activationLabel: String
        get() = activationUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

/**
 * The account-wide reading of a per-source flag.
 *
 * Null for an empty list: a switch on an account with nothing to refresh would
 * be a switch that does nothing, and the screen draws it disabled instead.
 */
internal fun autoSyncOf(sources: List<Source>): Boolean? =
    if (sources.isEmpty()) null else sources.all { it.autoSync }

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
