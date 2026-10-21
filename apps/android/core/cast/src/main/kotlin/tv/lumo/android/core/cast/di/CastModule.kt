package tv.lumo.android.core.cast.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumo.android.core.cast.GoogleCastRemotePlayback
import tv.lumo.android.core.player.cast.RemotePlayback

/**
 * Fills core:player's optional [RemotePlayback] (S16). Present in the phone
 * application only; the television's player finds the binding absent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CastModule {

    @Binds
    abstract fun remotePlayback(impl: GoogleCastRemotePlayback): RemotePlayback
}
