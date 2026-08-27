package tv.lumo.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tv.lumo.android.network.generated.model.Platform

/**
 * Which of the two applications this is.
 *
 * The one fact a `core:` module cannot work out for itself: the same code builds
 * `tv.lumo.android` and `tv.lumo.androidtv` (ADR 0004). Declaring it here rather
 * than detecting it at runtime keeps the answer truthful when `app-tv` is
 * installed on a phone emulator to check that it starts, which the AGENTS.md
 * describes as a normal thing to do.
 *
 * This is configuration, not logic — the reason a shell module is allowed to
 * contain it at all.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun platform(): Platform = Platform.ANDROID_MOBILE
}
