package tv.lumo.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S10B-06: every hand-written migration, against the schema Room exported for
 * each version.
 *
 * `DatabaseModule` has no `fallbackToDestructiveMigration`, on purpose: a cache
 * of fifteen thousand channels is not wiped on an update. The other side of that
 * choice is that a wrong migration is a crash at start-up, for everybody who
 * updates — the release lock `R020-16`. These tests are what stands between a
 * migration and that crash.
 *
 * `runMigrationsAndValidate` compares the migrated database with the exported
 * JSON of the target version, table by table, column by column, index by index.
 */
@RunWith(AndroidJUnit4::class)
class LumoDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(TEST_DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = LumoDatabase::class,
    )

    private val migrations = listOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )

    /** Each test starts from no file: a version 7 left by the previous one would be reopened, not created. */
    @Before
    fun noDatabaseYet() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun eachStepMatchesTheExportedSchemaOfItsTarget() {
        for (step in migrations) {
            instrumentation.targetContext.deleteDatabase(TEST_DB)
            helper.createDatabase(step.startVersion).close()
            helper.runMigrationsAndValidate(step.endVersion, listOf(step)).close()
        }
    }

    @Test
    fun theWholePathFromVersionOneKeepsTheCatalogue() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO category (id, source_id, external_id, name, content_type, position, channel_count) " +
                    "VALUES ('cat-1', 'src-1', 'c1', 'Généralistes', 'LIVE', 0, 1)",
            )
            execSQL(
                "INSERT INTO channel (id, source_id, category_id, external_id, name, logo_url, tvg_id, position, is_adult) " +
                    "VALUES ('ch-1', 'src-1', 'cat-1', 'e1', 'Chaîne 01', NULL, 'chaine01.test', 0, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(LATEST_VERSION, migrations)

        migrated.prepare("SELECT name, number, quality FROM channel WHERE id = 'ch-1'").use { row ->
            assertTrue("the channel cached before the update is still there", row.step())
            assertEquals("Chaîne 01", row.getText(0))
            // Added by 1 → 2 with no backfill: unknown until the next sync.
            assertTrue(row.isNull(1))
            assertTrue(row.isNull(2))
        }
        migrated.prepare("SELECT COUNT(*) FROM category").use { row ->
            row.step()
            assertEquals(1L, row.getLong(0))
        }
        migrated.close()
    }

    /**
     * A version 8 exported without its migration in [migrations] fails here:
     * the newest schema shipped to the test APK is the version the list must reach.
     */
    @Test
    fun theMigrationListReachesTheNewestExportedSchema() {
        val newest = instrumentation.context.assets
            .list(LumoDatabase::class.java.name)
            .orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .max()

        assertEquals(LATEST_VERSION, newest)
        assertEquals(1, migrations.first().startVersion)
        assertEquals(LATEST_VERSION, migrations.last().endVersion)
        migrations.zipWithNext { a, b -> assertEquals(a.endVersion, b.startVersion) }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** `@Database(version = …)` of [LumoDatabase]. */
        const val LATEST_VERSION = 7
    }
}
