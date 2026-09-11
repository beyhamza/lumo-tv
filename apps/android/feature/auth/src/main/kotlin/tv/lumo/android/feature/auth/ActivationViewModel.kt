package tv.lumo.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AuthRepository
import tv.lumo.android.core.data.repository.DevicePoll

/**
 * Activating a television from a phone (US-05, RFC 8628).
 *
 * <h2>Why this flow exists at all</h2>
 *
 * Typing an email and a password on a remote control is a punitive experience and
 * the first place people abandon a television application. So the set shows a
 * short code and a QR, and everything else happens on a device with a keyboard.
 *
 * <h2>Pending is the normal answer, and is never shown as one</h2>
 *
 * Every poll comes back `AUTHORIZATION_PENDING` until somebody approves, which is
 * to say: almost always, for as long as this screen is on display. A client that
 * rendered it would put a red message on a screen where nothing is wrong. It is
 * absorbed here, and `SLOW_DOWN` with it — that one adds five seconds to the
 * interval and keeps going, per the RFC.
 *
 * <h2>An expired code replaces itself, with nobody watching</h2>
 *
 * That is the requirement, and the reason for it is physical: the person walked
 * away to fetch their phone. There is no one in front of the set to press
 * anything, and a screen still showing a dead code has stopped working without
 * saying so. Expiry is noticed through the poll — the server answers
 * `EXPIRED_TOKEN` — rather than by a timer of our own, so there is one clock and
 * it is the server's.
 */
@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivationState())
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    /** Asks for a code, shows it, and polls until something happens. */
    fun start() {
        job?.cancel()

        job = viewModelScope.launch {
            while (isActive) {
                val code = when (val requested = auth.requestDeviceCode()) {
                    is LumoResult.Success -> requested.value
                    is LumoResult.Failure -> {
                        // No code, nothing to show. This is the one failure this
                        // screen states plainly, because there is no half of the
                        // flow that still works.
                        _state.update { it.copy(step = ActivationStep.Unavailable) }
                        return@launch
                    }
                }

                _state.update {
                    it.copy(
                        step = ActivationStep.Waiting(
                            userCode = code.userCode,
                            // The QR encodes the *complete* URI, so the nominal
                            // path involves no typing at all: point a phone at the
                            // screen and approve.
                            verificationUriComplete = code.verificationUriComplete.toString(),
                            verificationUri = code.verificationUri.toString(),
                            // Wall-clock, so the screen can count down without
                            // being told when it started looking.
                            expiresAtMillis = System.currentTimeMillis() + code.expiresIn * 1_000L,
                        ),
                    )
                }

                // Looping means the code expired and a new one is wanted. The
                // screen changes under a viewer who is not there, which is the
                // point.
                if (!pollUntilResolved(code.deviceCode, code.interval)) return@launch
            }
        }
    }

    /**
     * @return true when the code died and a fresh one should be requested; false
     * when this screen is finished, one way or another.
     */
    private suspend fun pollUntilResolved(deviceCode: String, initialInterval: Int): Boolean {
        var intervalSeconds = initialInterval

        while (true) {
            delay(intervalSeconds * 1_000L)

            when (auth.pollDeviceToken(deviceCode)) {
                // The session is open. `AppStartDecision` has already seen it and
                // the shell is rebuilding its graph; there is nothing to navigate.
                DevicePoll.Approved -> return false

                DevicePoll.Pending -> Unit

                // RFC 8628: five seconds more, and keep going.
                DevicePoll.SlowDown -> intervalSeconds += SLOW_DOWN_STEP_SECONDS

                DevicePoll.Denied -> {
                    _state.update { it.copy(step = ActivationStep.Denied) }
                    return false
                }

                DevicePoll.NeedsNewCode -> return true

                // A poll that did not get through. The code on screen is still
                // valid and the next attempt may well succeed, so nothing is said
                // and nothing is thrown away.
                is DevicePoll.Unavailable -> Unit
            }
        }
    }
}

sealed interface ActivationStep {

    /** Asking for a code. */
    data object Loading : ActivationStep

    data class Waiting(
        val userCode: String,
        val verificationUriComplete: String,
        val verificationUri: String,
        /** When the code stops being accepted; a new one is requested by then. */
        val expiresAtMillis: Long,
    ) : ActivationStep

    /** The user refused on their phone. */
    data object Denied : ActivationStep

    /** No code could be obtained at all. */
    data object Unavailable : ActivationStep
}

data class ActivationState(val step: ActivationStep = ActivationStep.Loading)

/** RFC 8628 §3.5: on `slow_down`, add five seconds and carry on. */
private const val SLOW_DOWN_STEP_SECONDS = 5
