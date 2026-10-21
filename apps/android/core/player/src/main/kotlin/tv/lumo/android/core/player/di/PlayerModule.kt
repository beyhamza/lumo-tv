package tv.lumo.android.core.player.di

import android.util.Log
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.Media3LumoPlayer
import tv.lumo.android.core.player.cast.CastingLumoPlayer
import tv.lumo.android.core.player.cast.RemotePlayback

/**
 * One player for the process.
 *
 * A codec is a scarce, device-limited resource — cheap TV boxes have one or two
 * hardware decoders — and the product plays one stream at a time on both form
 * factors. A player per screen would leave decoders held by screens the user has
 * navigated away from, and the symptom is the next channel failing to start on
 * exactly the devices that can least afford it.
 *
 * <h2>And the Chromecast it may send to (S16)</h2>
 *
 * The phone application binds a [RemotePlayback] (`core:cast`, Google Cast SDK);
 * the television application binds none and ships no SDK. Either way the screens
 * get one [LumoPlayer], which plays on the phone unless a receiver is connected.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @BindsOptionalOf
    internal abstract fun remotePlayback(): RemotePlayback

    internal companion object {

        @Provides
        @Singleton
        fun lumoPlayer(local: Media3LumoPlayer, remote: Optional<RemotePlayback>): LumoPlayer =
            CastingLumoPlayer(
                local = local,
                remote = remote.orElse(null),
                // The SDK's thread, and the one the phone's player already lives on.
                scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
                log = { Log.i(CAST_LOG_TAG, it) },
            )

        private const val CAST_LOG_TAG = "LumoCast"
    }
}
