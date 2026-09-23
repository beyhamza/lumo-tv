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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.Device
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Platform
import tv.lumo.android.network.generated.model.Source

/**
 * The account, its devices, and the way out of it (US-04, US-025).
 *
 * <h2>Five sections, and only what is operational in them (S8-06)</h2>
 *
 * Account and devices, My sources, Application, Help and information — and no
 * Playback, whose settings do not exist yet (sprint 13). The rule of the sprint
 * is that what is not delivered is not shown: no `[mock]` row, no "coming soon"
 * switch. So the two rows this screen used to draw with nothing behind them —
 * the language *choice* and playback on mobile data — are gone; the language is
 * shown read-only, because that much is true.
 *
 * <h2>Why signing out lives here</h2>
 *
 * US-04's second scenario ends "l'appareil doit se reconnecter", and until this
 * existed there was no way to get back to that state: a device that signed in
 * once stayed signed in until the application was uninstalled. It is behind a
 * confirmation since S8-06, with Cancel as the default: on a phone the row sits
 * under the thumb, on a television `OK` pressed one time too many lands on it.
 *
 * <h2>Devices: read once, revoked one at a time, never guessed</h2>
 *
 * `GET /me/devices` says which row is this installation (`is_current`) and when
 * each one was last seen. Both come from the server: the screen marks the row
 * it is told to and prints "last activity", never "online" — the contract is
 * explicit that `last_seen_at` is not a presence. Revoking goes through
 * `DELETE /me/devices/{id}` and, on a refusal, **keeps the device and offers a
 * retry** (docs/design/0.2.0/settings.md): a row that disappeared after a
 * failed call would be the screen announcing a success it did not get.
 *
 * <h2>Nothing here navigates</h2>
 *
 * Signing out drops the session, `AppStartDecision` sees it go, and the shell
 * rebuilds its graph onto the way in — with the back stack cleared, which is the
 * part that matters: `BACK` must not walk back into an account that is gone.
 *
 * <h2>The rules are on the state, the plumbing is here</h2>
 *
 * Which device is this one, what a confirmation does, what a refused revoke
 * leaves behind: all pure functions of [SettingsUiState], pinned by
 * `SettingsUiStateTest` without a session or a server. This class fetches, calls
 * and applies.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val sources: SourceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            activationUrl = BuildConfig.ACTIVATION_URL,
            appVersion = appVersion(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Once per session rather than on every emission: the session flow
            // re-emits on each token rotation, and re-reading an address that
            // cannot have changed would put a request on the wire every hour.
            account.isSignedIn.distinctUntilChanged().collect { signedIn ->
                if (signedIn) {
                    _state.update { it.copy(signedIn = true) }
                    loadAccount()
                    loadSources()
                    loadDevices()
                } else {
                    // Everything read after the session goes with it, in one move.
                    _state.update { it.signedOut() }
                }
            }
        }
    }

    // ---- signing out --------------------------------------------------------

    /** Opens the confirmation. Nothing happens until [confirmSignOut]. */
    fun askSignOut() = _state.update { it.askSignOut() }

    /**
     * Ends the session on this device.
     *
     * The server is told first so the seat is freed, but the local session goes
     * either way — that is [AccountRepository]'s doing and it is deliberate:
     * someone who taps "sign out" on a train and stays signed in has been lied to
     * by the application, and the refresh token they are still holding is the
     * thing that made the tap urgent.
     */
    fun confirmSignOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(confirmation = null, signingOut = true) }

        viewModelScope.launch {
            account.signOut()
            // Usually this screen is gone by now — the graph is rebuilt the
            // moment the session disappears. Reset anyway rather than leave a
            // spinner behind if it is not.
            _state.update { it.copy(signingOut = false) }
        }
    }

    // ---- devices -------------------------------------------------------------

    /** Opens the confirmation that names [device]. Nothing happens until [confirmRevoke]. */
    fun askRevoke(device: Device) = _state.update { it.askRevoke(device) }

    /** Closes whichever confirmation is open. Refused while a call is on the wire. */
    fun dismissConfirmation() = _state.update { it.dismissConfirmation() }

    /**
     * `DELETE /me/devices/{id}` for the device the confirmation names.
     *
     * A refusal keeps the dialog open with the reason and both buttons live: the
     * design keeps the device on a failure and proposes a retry. `DEVICE_NOT_FOUND`
     * is the one refusal that is not one — the device is already gone, which is
     * what was asked — and the list is read again so the row disappears on the
     * server's word rather than on this screen's.
     */
    fun confirmRevoke() {
        val asked = _state.value.confirmation as? SettingsConfirmation.Revoke ?: return
        if (asked.busy) return
        _state.update { it.revoking() }

        viewModelScope.launch {
            when (val result = account.revokeDevice(asked.device.id.toString())) {
                is LumoResult.Success -> _state.update { it.revoked(asked.device.id.toString()) }
                is LumoResult.Failure ->
                    if (result.error.isAlreadyGone()) {
                        _state.update { it.revoked(asked.device.id.toString()) }
                        loadDevices()
                    } else {
                        _state.update { it.revokeRefused(result.error) }
                    }
            }
        }
    }

    /** "Try again" on a device list that could not be read. */
    fun reloadDevices() = loadDevices()

    private fun loadAccount() {
        viewModelScope.launch {
            val user = account.me().valueOrNull() ?: return@launch
            _state.update { it.copy(email = user.email, displayName = user.displayName) }
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            val list = sources.sources().valueOrNull() ?: return@launch
            _state.update { it.copy(sources = list, sourceCount = list.size) }
        }
    }

    private fun loadDevices() {
        _state.update { it.copy(devices = DevicesState.Loading) }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    devices = when (val result = account.devices()) {
                        is LumoResult.Success -> DevicesState.Loaded(result.value)
                        // Not an empty list: "no other device" would be a claim
                        // nobody verified (docs/design/0.2.0/settings.md).
                        is LumoResult.Failure -> DevicesState.Unavailable
                    },
                )
            }
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
}

/**
 * The account's devices, as the screen holds them.
 *
 * Three answers and not a nullable list, so that "could not be read" is never
 * drawn as "none": the design asks for a retry there, and a list that failed to
 * load is not an empty one.
 */
sealed interface DevicesState {

    data object Loading : DevicesState

    /** `GET /me/devices` failed. Worth retrying; not a fact about the account. */
    data object Unavailable : DevicesState

    /** The server's list, in the server's order — most recently seen first. */
    data class Loaded(val devices: List<Device>) : DevicesState
}

/**
 * A question the screen is asking, over its content.
 *
 * One value rather than two booleans: a sign-out and a revoke asked at once is a
 * state with no drawing, and a single type makes it unrepresentable.
 */
sealed interface SettingsConfirmation {

    /** "Sign out of this device?" Cancel is the default answer. */
    data object SignOut : SettingsConfirmation

    /**
     * "Disconnect this device?", naming it.
     *
     * @param busy the call is on the wire; both buttons wait.
     * @param failure the server's refusal, kept under the question so that the
     * user can try again from where they were. Null until there is one.
     */
    data class Revoke(
        val device: Device,
        val busy: Boolean = false,
        val failure: LumoError? = null,
    ) : SettingsConfirmation
}

/** What the settings surfaces render. */
data class SettingsUiState(
    val signedIn: Boolean = false,
    /** The account's address, once it has been read. Null while unknown. */
    val email: String? = null,
    /** What the account is called on its devices, when the user gave one. */
    val displayName: String? = null,
    val signingOut: Boolean = false,

    /** How many sources the account holds. Null until read, or when the read failed. */
    val sourceCount: Int? = null,
    /** The sources themselves, for a surface that draws them as cards (TV5). */
    val sources: List<Source> = emptyList(),

    val devices: DevicesState = DevicesState.Loading,

    /** Where a television is paired from a phone — `LUMO_ACTIVATION_URL`. */
    val activationUrl: String = "",
    /** `versionName` of the running application. Null only if the package is unreadable. */
    val appVersion: String? = null,

    val confirmation: SettingsConfirmation? = null,
) {

    /** The letter in the account disc: the address's first character, or nothing. */
    val initial: String
        get() = email?.trim()?.firstOrNull()?.uppercase().orEmpty()

    /** `lumo.tv/activate`, as the mock-up prints it — the URL without its scheme. */
    val activationLabel: String
        get() = SettingsLinks.label(activationUrl)

    /**
     * The row the server marked `is_current`, or null while the list is not
     * there — or when the server marked none, which it does for a list read
     * with a token this list does not know. Never guessed from the platform: two
     * phones on one account are two rows with the same platform.
     */
    val currentDevice: Device?
        get() = loadedDevices.firstOrNull { it.isCurrent }

    /** Every other installation, in the server's order. */
    val otherDevices: List<Device>
        get() = loadedDevices.filterNot { it.isCurrent }

    private val loadedDevices: List<Device>
        get() = (devices as? DevicesState.Loaded)?.devices.orEmpty()

    // ---- confirmations, as pure transitions ----------------------------------

    fun askSignOut(): SettingsUiState =
        if (signingOut || confirmation != null) this else copy(confirmation = SettingsConfirmation.SignOut)

    fun askRevoke(device: Device): SettingsUiState =
        if (confirmation != null) this else copy(confirmation = SettingsConfirmation.Revoke(device))

    /** Closes the question — unless a revoke is on the wire, which must be seen through. */
    fun dismissConfirmation(): SettingsUiState =
        if ((confirmation as? SettingsConfirmation.Revoke)?.busy == true) this else copy(confirmation = null)

    fun revoking(): SettingsUiState {
        val asked = confirmation as? SettingsConfirmation.Revoke ?: return this
        return copy(confirmation = asked.copy(busy = true, failure = null))
    }

    /** The server refused: the device stays, the question stays, the reason is under it. */
    fun revokeRefused(error: LumoError): SettingsUiState {
        val asked = confirmation as? SettingsConfirmation.Revoke ?: return this
        return copy(confirmation = asked.copy(busy = false, failure = error))
    }

    /** The server agreed: the row goes, the question closes. */
    fun revoked(deviceId: String): SettingsUiState = copy(
        confirmation = null,
        devices = when (devices) {
            is DevicesState.Loaded ->
                DevicesState.Loaded(devices.devices.filterNot { it.id.toString() == deviceId })
            else -> devices
        },
    )

    /** Everything read after the session, dropped with it. The build's facts stay. */
    fun signedOut(): SettingsUiState = SettingsUiState(
        activationUrl = activationUrl,
        appVersion = appVersion,
    )
}

/**
 * `DEVICE_NOT_FOUND`: the device this screen was asked to remove is already
 * gone — removed from the website, or signed out by itself. Not a failure of the
 * request but the state it was asking for.
 */
private fun LumoError.isAlreadyGone(): Boolean =
    this is LumoError.Api && code == ErrorCode.DEVICE_NOT_FOUND

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

/**
 * What a device row is called: the name the user gave it, else its model, else
 * nothing — and the screen falls back to the platform's own label. In that
 * order because it is the order of how much somebody chose it.
 */
internal fun Device.givenTitle(): String? =
    name?.trim()?.takeIf { it.isNotEmpty() } ?: model?.trim()?.takeIf { it.isNotEmpty() }

/** The platform's label, for a device with no name and no model. */
@StringRes
internal fun Platform.labelRes(): Int = when (this) {
    Platform.ANDROID_MOBILE -> R.string.feature_settings_platform_android_mobile
    Platform.ANDROID_TV -> R.string.feature_settings_platform_android_tv
    Platform.WEB -> R.string.feature_settings_platform_web
}
