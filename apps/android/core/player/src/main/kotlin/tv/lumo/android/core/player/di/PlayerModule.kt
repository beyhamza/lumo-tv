package tv.lumo.android.core.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tv.lumo.android.core.player.LumoPlayer
import tv.lumo.android.core.player.Media3LumoPlayer

/**
 * One player for the process.
 *
 * A codec is a scarce, device-limited resource — cheap TV boxes have one or two
 * hardware decoders — and the product plays one stream at a time on both form
 * factors. A player per screen would leave decoders held by screens the user has
 * navigated away from, and the symptom is the next channel failing to start on
 * exactly the devices that can least afford it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    internal abstract fun lumoPlayer(impl: Media3LumoPlayer): LumoPlayer
}
