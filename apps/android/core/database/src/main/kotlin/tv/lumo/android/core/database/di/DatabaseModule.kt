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
import tv.lumo.android.core.database.dao.CategoryDao
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.dao.FavoriteDao
import tv.lumo.android.core.database.dao.RecentChannelDao
import tv.lumo.android.core.database.paging.CataloguePager

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
    @Singleton
    fun cataloguePager(channelDao: ChannelDao): CataloguePager = CataloguePager(channelDao)

    private const val DATABASE_NAME = "lumo-catalogue.db"
}
