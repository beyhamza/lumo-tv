package tv.lumo.android.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.internal.DataStoreDirectViewStore
import tv.lumo.android.core.data.internal.DirectViewStore
import tv.lumo.android.core.data.repository.DefaultDirectViewRepository
import tv.lumo.android.core.data.repository.DirectViewRepository

/**
 * Names the Preferences DataStore the Direct view memory lives in.
 *
 * A qualifier because the type says nothing about what a file holds: this module
 * already owns the active-source store, and the second `DataStore<Preferences>`
 * to appear would otherwise be injected wherever the first was meant.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DirectViewPreferences

@Module
@InstallIn(SingletonComponent::class)
internal object DirectViewModule {

    /**
     * A singleton, and it has to be: two DataStore instances over one file is an
     * error DataStore raises at the first read, not a subtle bug.
     */
    @Provides
    @Singleton
    @DirectViewPreferences
    fun directViewPreferences(
        @ApplicationContext context: Context,
        @Dispatcher(LumoDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Losing this file costs one default view — Channels instead of Guide —
        // so a corrupted one is replaced rather than reported.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile(DIRECT_VIEW_FILE) },
    )

    @Provides
    @Singleton
    fun directViewRepository(store: DirectViewStore): DirectViewRepository =
        DefaultDirectViewRepository(store)

    private const val DIRECT_VIEW_FILE = "direct_view"
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DirectViewBindingsModule {

    @Binds
    @Singleton
    abstract fun directViewStore(impl: DataStoreDirectViewStore): DirectViewStore
}
