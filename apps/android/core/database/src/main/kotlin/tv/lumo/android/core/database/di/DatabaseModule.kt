package tv.lumo.android.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tv.lumo.android.core.database.LumoDatabase
import tv.lumo.android.core.database.MIGRATION_1_2
import tv.lumo.android.core.database.MIGRATION_2_3
import tv.lumo.android.core.database.MIGRATION_3_4
import tv.lumo.android.core.database.MIGRATION_4_5
import tv.lumo.android.core.database.MIGRATION_5_6
import tv.lumo.android.core.database.MIGRATION_6_7
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.dao.EpgDao
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.dao.RecentChannelDao
import tv.lumo.android.core.database.dao.SeriesDao
import tv.lumo.android.core.database.dao.VodDao
import tv.lumo.android.core.database.paging.CataloguePager
import tv.lumo.android.core.database.paging.SeriesPager
import tv.lumo.android.core.database.paging.VodPager

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LumoDatabase =
        Room.databaseBuilder(context, LumoDatabase::class.java, DATABASE_NAME)
            // No fallbackToDestructiveMigration(). It would turn a forgotten
            // migration into a silent wipe of the user's synchronised
            // catalogue — recoverable, but it means a re-sync of fifteen
            // thousand channels over someone's mobile data. Write the migration.
            .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                        )
            .build()

    @Provides
    fun channelDao(database: LumoDatabase): ChannelDao = database.channelDao()

    @Provides
    fun categoryDao(database: LumoDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun favoriteDao(database: LumoDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun recentChannelDao(database: LumoDatabase): RecentChannelDao = database.recentChannelDao()

    @Provides
    fun vodDao(database: LumoDatabase): VodDao = database.vodDao()

    @Provides
    fun seriesDao(database: LumoDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun epgDao(database: LumoDatabase): EpgDao = database.epgDao()

    @Provides
    @Singleton
    fun cataloguePager(channelDao: ChannelDao): CataloguePager = CataloguePager(channelDao)

    @Provides
    @Singleton
    fun vodPager(vodDao: VodDao): VodPager = VodPager(vodDao)

    @Provides
    @Singleton
    fun seriesPager(seriesDao: SeriesDao): SeriesPager = SeriesPager(seriesDao)

    private const val DATABASE_NAME = "lumo-catalogue.db"
}
