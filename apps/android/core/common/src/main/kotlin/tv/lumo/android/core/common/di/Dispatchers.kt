package tv.lumo.android.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dispatchers are injected, never referenced as `Dispatchers.IO` from inside a
 * class. That is what makes a unit test able to substitute a test dispatcher and
 * run without a real clock or a real thread pool.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: LumoDispatcher)

enum class LumoDispatcher { IO, Default }

/**
 * A scope that lives as long as the process.
 *
 * For work that must outlive the screen that started it — writing a token after
 * a refresh, flushing playback progress — and nothing else. A ViewModel's own
 * scope is the right answer for anything the user is looking at.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(LumoDispatcher.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(LumoDispatcher.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(
        @Dispatcher(LumoDispatcher.Default) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
