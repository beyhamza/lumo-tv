package tv.lumo.android.core.auth.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.store.DataStoreSessionStore
import tv.lumo.android.core.auth.store.SessionSerializer
import tv.lumo.android.core.auth.store.SessionStore
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AuthStoreModule {

    @Provides
    @Singleton
    fun sessionDataStore(
        @ApplicationContext context: Context,
        serializer: SessionSerializer,
        @Dispatcher(LumoDispatcher.IO) io: CoroutineDispatcher,
    ): DataStore<SessionTokens?> = DataStoreFactory.create(
        serializer = serializer,
        // An unreadable session is not an error to surface: the Keystore key can
        // legitimately disappear (restored backup, changed lock screen). Replace
        // the file and let the user sign in again rather than crash on start.
        corruptionHandler = ReplaceFileCorruptionHandler { null },
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.dataStoreFile(SESSION_FILE) },
    )

    private const val SESSION_FILE = "session.bin"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

    @Binds
    @Singleton
    abstract fun sessionStore(impl: DataStoreSessionStore): SessionStore
}

private fun Context.dataStoreFile(fileName: String) =
    java.io.File(applicationContext.filesDir, "datastore/$fileName")
