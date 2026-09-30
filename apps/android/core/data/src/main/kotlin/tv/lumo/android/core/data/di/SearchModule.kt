package tv.lumo.android.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tv.lumo.android.core.data.repository.DefaultSearchRepository
import tv.lumo.android.core.data.repository.SearchRepository

/**
 * Binds the unified search implementation (S10-01).
 *
 * A module rather than a constructor binding because [SearchRepository] is an
 * interface: the feature depends on the contract, and the three repositories it
 * composes stay this module's business. Same shape as `EpgModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchModule {

    @Binds
    @Singleton
    abstract fun searchRepository(impl: DefaultSearchRepository): SearchRepository
}
