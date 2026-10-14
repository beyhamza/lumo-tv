package tv.lumo.android.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tv.lumo.android.core.data.repository.DefaultWatchlistRepository
import tv.lumo.android.core.data.repository.WatchlistRepository

/** "My list" (US-022): an interface so view models can be tested with a plain fake. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WatchlistModule {

    @Binds
    @Singleton
    abstract fun watchlistRepository(impl: DefaultWatchlistRepository): WatchlistRepository
}
