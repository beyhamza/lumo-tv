package tv.lumo.android.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import tv.lumo.android.core.data.repository.DefaultEpgRepository
import tv.lumo.android.core.data.repository.EpgRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EpgModule {

    /**
     * One instance: the purge schedule and the split loader's two-permit
     * semaphore are per process, and two instances would be two schedules and
     * four requests in flight.
     */
    @Binds
    @Singleton
    abstract fun epgRepository(impl: DefaultEpgRepository): EpgRepository

    companion object {

        /**
         * The clock the guide is read against, injected so that ages and purges
         * are things a test can move (`Dispatchers.kt` makes the same argument
         * for dispatchers). UTC: every instant here is compared, never shown;
         * the device's zone is applied by the formatting helper at display time.
         */
        @Provides
        @Singleton
        fun clock(): Clock = Clock.systemUTC()
    }
}
