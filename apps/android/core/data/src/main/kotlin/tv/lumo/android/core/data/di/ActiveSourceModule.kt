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
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.common.di.ApplicationScope
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.internal.ActiveSourceStore
import tv.lumo.android.core.data.internal.AndroidConnectivityMonitor
import tv.lumo.android.core.data.internal.ConnectivityMonitor
import tv.lumo.android.core.data.internal.DataStoreActiveSourceStore
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.DefaultActiveSourceRepository
import tv.lumo.android.core.data.repository.SourceRepository

/**
 * Names the one Preferences DataStore this module owns.
 *
 * A qualifier although there is a single `DataStore<Preferences>` today: the
 * type says nothing about what a file holds, and the second one to appear would
 * otherwise be injected wherever this one was meant.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ActiveSourcePreferences

@Module
@InstallIn(SingletonComponent::class)
internal object ActiveSourceModule {

    /**
     * A singleton, and it has to be: two DataStore instances over one file is an
     * error DataStore raises at the first read, not a subtle bug.
     */
    @Provides
    @Singleton
    @ActiveSourcePreferences
    fun activeSourcePreferences(
        @ApplicationContext context: Context,
        @Dispatcher(LumoDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Losing this file costs one question — "which source?" — so a corrupted
        // one is replaced rather than reported.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile(ACTIVE_SOURCE_FILE) },
    )

    /**
     * One instance for the whole process. Four screens and the shell read the
     * same [ActiveSourceRepository.state]; a second instance would be a second
     * opinion about which source is active.
     *
     * The application scope, because the repository follows the session for as
     * long as the process lives and no screen's lifetime is the right one.
     */
    @Provides
    @Singleton
    fun activeSourceRepository(
        session: SessionManager,
        sources: SourceRepository,
        store: ActiveSourceStore,
        @ApplicationScope scope: CoroutineScope,
    ): ActiveSourceRepository = DefaultActiveSourceRepository(session, sources, store, scope)

    private const val ACTIVE_SOURCE_FILE = "active_source"
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ActiveSourceBindingsModule {

    @Binds
    @Singleton
    abstract fun activeSourceStore(impl: DataStoreActiveSourceStore): ActiveSourceStore

    /** What tells the playback watcher that the network came back (C4, D5). */
    @Binds
    @Singleton
    abstract fun connectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor
}
