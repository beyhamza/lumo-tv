package tv.lumo.android.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.repository.PlaybackPreferencesRepository

/**
 * Wi-Fi only playback on Android mobile (S13-05, Q7 of
 * `docs/design/0.2.0/player-settings-cases.md`).
 *
 * The option concerns **video**, not the catalogue: nothing here is consulted
 * by a list, a sheet or a search, only by what launches a stream.
 */

/**
 * What the platform says about the network the device is using right now.
 *
 * The default network's capabilities, not "is there a Wi-Fi interface": a phone
 * can be associated with a Wi-Fi access point and still be sending through the
 * cellular radio because the access point has no Internet.
 */
data class NetworkFacts(
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val ethernet: Boolean = false,
    val vpn: Boolean = false,
    /** The platform checked this network reaches the Internet (not a captive portal). */
    val validated: Boolean = false,
)

enum class NetworkVerdict {
    /** Video may load. */
    ALLOWED,

    /** Mobile data or Ethernet, and the viewer asked for Wi-Fi only. */
    WIFI_REQUIRED,

    /** A VPN over an unknown network, or a network of unknown kind: Wi-Fi cannot be confirmed. */
    WIFI_UNCONFIRMED,

    /** No usable connection: none at all, or Wi-Fi without Internet (captive portal). */
    NO_CONNECTION,
}

/**
 * Q7, as a function.
 *
 * | Network in use, mobile data **not** allowed | Verdict |
 * |---|---|
 * | Wi-Fi, validated — billed or not | allowed |
 * | VPN whose underlying network is Wi-Fi | allowed |
 * | Mobile data or Ethernet | Wi-Fi required |
 * | VPN over mobile, unknown kind | Wi-Fi cannot be confirmed |
 * | Wi-Fi without Internet, nothing at all | no connection |
 *
 * Metering is deliberately not read: a billed Wi-Fi is still Wi-Fi (Q7), and an
 * unbilled cellular plan is still cellular. When in doubt, block — without
 * claiming a proof the platform does not give.
 */
fun playbackVerdict(mobileDataAllowed: Boolean, facts: NetworkFacts?): NetworkVerdict = when {
    mobileDataAllowed -> NetworkVerdict.ALLOWED
    facts == null -> NetworkVerdict.NO_CONNECTION
    // Android reports the transports of a VPN's underlying network alongside
    // TRANSPORT_VPN: a VPN over Wi-Fi carries both flags.
    facts.wifi && facts.validated -> NetworkVerdict.ALLOWED
    facts.wifi -> NetworkVerdict.NO_CONNECTION
    facts.vpn -> NetworkVerdict.WIFI_UNCONFIRMED
    facts.cellular || facts.ethernet -> NetworkVerdict.WIFI_REQUIRED
    else -> NetworkVerdict.WIFI_UNCONFIRMED
}

/** The current verdict for a video launch, and its changes. */
interface PlaybackNetworkGate {
    suspend fun verdict(): NetworkVerdict

    /** The current verdict first, then each change. */
    val verdicts: Flow<NetworkVerdict>
}

/**
 * [PlaybackNetworkGate] over `ConnectivityManager`.
 *
 * **A television is never gated**: the option exists on Android mobile only, and
 * a TV box on Ethernet is the normal case, not a data plan to protect.
 */
@Singleton
internal class AndroidPlaybackNetworkGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PlaybackPreferencesRepository,
) : PlaybackNetworkGate {

    private val television: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun verdict(): NetworkVerdict {
        if (television) return NetworkVerdict.ALLOWED
        return playbackVerdict(preferences.mobileDataAllowed(), currentFacts())
    }

    override val verdicts: Flow<NetworkVerdict> =
        if (television) {
            flowOf(NetworkVerdict.ALLOWED)
        } else {
            combine(preferences.mobileDataAllowedChanges, facts()) { allowed, facts ->
                playbackVerdict(allowed, facts)
            }.distinctUntilChanged()
        }

    private fun currentFacts(): NetworkFacts? {
        val manager = manager ?: return null
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)?.asFacts()
    }

    /** The default network's capabilities, each time they change. Null while there is none. */
    private fun facts(): Flow<NetworkFacts?> = callbackFlow {
        trySend(currentFacts())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.asFacts())
            }

            override fun onLost(network: Network) {
                trySend(currentFacts())
            }
        }
        val registered = manager != null &&
            runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    private fun NetworkCapabilities.asFacts() = NetworkFacts(
        wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        ethernet = hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        vpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}

/**
 * Why video is not playing, when the network is the reason (S13-05).
 *
 * @property interrupted the network went away **during** playback: the stream
 *   was stopped — no further media request — and its position kept.
 * @property networkBack the network allows video again. Nothing restarts by
 *   itself (Q7): the screen offers *Resume*, which checks again first.
 */
data class NetworkBlock(
    val reason: NetworkVerdict,
    val interrupted: Boolean,
    val networkBack: Boolean = false,
)

/** The title of the panel a mobile player shows for this block. */
val NetworkBlock.titleRes: Int
    get() = when {
        networkBack -> R.string.core_data_network_back
        reason == NetworkVerdict.NO_CONNECTION -> R.string.core_data_network_no_connection
        else -> R.string.core_data_network_wifi_required
    }

/** Its explanation, or null when the title says it all ("Wi-Fi available. Resume?"). */
val NetworkBlock.bodyRes: Int?
    get() = when {
        networkBack -> null
        reason == NetworkVerdict.NO_CONNECTION -> R.string.core_data_network_no_connection_body
        reason == NetworkVerdict.WIFI_UNCONFIRMED -> R.string.core_data_network_unconfirmed_body
        else -> R.string.core_data_network_wifi_required_body
    }

/**
 * The next [NetworkBlock] after a verdict, for a screen that is blocked.
 *
 * Pure so the table is tested without a radio: a block never lifts itself, it
 * only learns that the network is back; a later loss takes that back.
 */
fun NetworkBlock.after(verdict: NetworkVerdict): NetworkBlock =
    if (verdict == NetworkVerdict.ALLOWED) {
        copy(networkBack = true)
    } else {
        copy(reason = verdict, networkBack = false)
    }

/**
 * One player screen's use of the [PlaybackNetworkGate].
 *
 * Every launch — opening, retrying, resuming, the next episode by itself — goes
 * through [admit] (Q7: "tous les départs passent par la même règle"). While a
 * stream plays, a verdict that turns against it calls the screen's `interrupt`,
 * which saves the position and stops the player: a pause alone would keep
 * buffering over the network the viewer refused.
 */
class PlaybackNetworkGuard @Inject constructor(
    private val gate: PlaybackNetworkGate,
) {
    private val _block = MutableStateFlow<NetworkBlock?>(null)
    val block: StateFlow<NetworkBlock?> = _block.asStateFlow()

    private var playing = false
    private var watcher: Job? = null

    /** Whether a launch may go ahead now. When not, [block] says why. */
    suspend fun admit(): Boolean {
        val verdict = gate.verdict()
        return if (verdict == NetworkVerdict.ALLOWED) {
            _block.value = null
            playing = true
            true
        } else {
            _block.value = NetworkBlock(reason = verdict, interrupted = playing || _block.value?.interrupted == true)
            playing = false
            false
        }
    }

    /** Starts following the network for this screen. Idempotent. */
    fun watch(scope: CoroutineScope, interrupt: () -> Unit) {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            gate.verdicts.collect { verdict ->
                val blocked = _block.value
                when {
                    blocked != null -> _block.value = blocked.after(verdict)
                    playing && verdict != NetworkVerdict.ALLOWED -> {
                        playing = false
                        _block.value = NetworkBlock(reason = verdict, interrupted = true)
                        interrupt()
                    }
                }
            }
        }
    }

    /** The screen left, or the stream ended: nothing is playing any more. */
    fun idle() {
        playing = false
    }
}
