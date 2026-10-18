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
import java.util.Locale
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.AndroidPlaybackNetworkGate
import tv.lumo.android.core.data.PlaybackNetworkGate
import tv.lumo.android.core.data.repository.DataStorePlaybackPreferences
import tv.lumo.android.core.data.repository.PlaybackPreferencesRepository

/** Names the Preferences DataStore playback preferences live in (S13-02/03/05). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PlaybackPreferencesFile

@Module
@InstallIn(SingletonComponent::class)
internal object PlaybackPreferencesModule {

    @Provides
    @Singleton
    @PlaybackPreferencesFile
    fun playbackPreferencesFile(
        @ApplicationContext context: Context,
        @Dispatcher(LumoDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Losing this file costs the defaults of Q6, nothing more.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile(PLAYBACK_PREFERENCES_FILE) },
    )

    /**
     * Not cleared at sign-out, unlike the active source and the Direct view
     * (`AccountDataCleaner`): Q6 asks that an account coming back to this
     * installation finds its languages again (PS-02). Keyed by account, they are
     * never applied to another one.
     */
    @Provides
    @Singleton
    fun playbackPreferences(
        @PlaybackPreferencesFile file: DataStore<Preferences>,
        session: SessionManager,
    ): PlaybackPreferencesRepository = DataStorePlaybackPreferences(
        preferences = file,
        accountId = { session.current()?.userId },
        accountIds = session.sessions.map { it?.userId }.distinctUntilChanged(),
        interfaceLanguage = { Locale.getDefault().language },
    )

    private const val PLAYBACK_PREFERENCES_FILE = "playback_preferences"
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackNetworkModule {

    @Binds
    @Singleton
    abstract fun playbackNetworkGate(impl: AndroidPlaybackNetworkGate): PlaybackNetworkGate
}
