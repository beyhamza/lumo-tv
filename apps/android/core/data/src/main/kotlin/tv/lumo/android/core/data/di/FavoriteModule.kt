package tv.lumo.android.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumo.android.core.data.repository.FavoriteLibrary
import tv.lumo.android.core.data.repository.FavoriteRepository

/**
 * The favourites screen's view of [FavoriteRepository] (S11-03). The same
 * singleton: the channel grid's hearts, the home rail and the library share one
 * cache, so the interface is a second name for it, never a second instance.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FavoriteModule {

    @Binds
    abstract fun favoriteLibrary(impl: FavoriteRepository): FavoriteLibrary
}
