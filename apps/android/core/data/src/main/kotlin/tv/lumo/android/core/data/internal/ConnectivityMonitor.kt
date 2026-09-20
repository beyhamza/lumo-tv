package tv.lumo.android.core.data.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop

/**
 * Says when the device gets a network back.
 *
 * An interface so that what listens to it can be tested with a plain flow: the
 * Android half below cannot run on a JVM, and the rule worth testing — "a
 * reconnection is a reason to ask the server again" — does not need it to.
 */
internal interface ConnectivityMonitor {

    /**
     * One emission each time a default network becomes available **again**.
     *
     * Not the network that is already there when somebody starts listening: that
     * is not a reconnection, and treating it as one would fire a request the
     * moment every player opens.
     */
    val regained: Flow<Unit>
}

/**
 * [ConnectivityMonitor] over `ConnectivityManager`.
 *
 * The permission it needs, `ACCESS_NETWORK_STATE`, is declared by `core:network`
 * and reaches both applications through the manifest merger.
 *
 * Registration failures are swallowed into a flow that never emits. Some vendor
 * builds throw from `registerDefaultNetworkCallback` past a callback quota, and
 * the consequence here is benign: the 60-second clock of
 * [tv.lumo.android.core.data.PlaybackSourceWatcher] still runs, only the early
 * check on reconnection is lost.
 */
@Singleton
internal class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    override val regained: Flow<Unit> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }

        val registered = manager != null &&
            runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess

        awaitClose {
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }
        // `onAvailable` fires once on registration when a network is already up.
        // Without one at registration nothing fires, and the first real emission
        // is dropped too: one early check lost, against a request at every player
        // opening — the clock covers the difference.
        .drop(1)
}
