package tv.lumo.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.lumo.android.core.data.internal.DataStoreDirectViewStore

/**
 * The file behind the Direct view memory, read and written for real (S9-04).
 *
 * A Preferences DataStore runs on a plain JVM, so this is the actual
 * implementation over an actual file — no emulator, the rule
 * `apps/android/AGENTS.md` §8 sets for every test here.
 *
 * What it guards is the key scheme and the one fact stored. The in-memory fake a
 * view model test would use is keyed by source *by construction*; only this test
 * says the real one is too, and a single shared key would pass every other test
 * while handing one source's view to another. The fourth test goes further and
 * reads the raw preferences, because GD-02 does not stop at "the interface has
 * no search": nothing beside the view may reach the file.
 */
class DirectViewStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `each source has its own entry`() = runTest {
        open { store, _ ->
            store.write("source-a", DirectView.Guide)
            store.write("source-b", DirectView.Channels)

            assertThat(store.read("source-a")).isEqualTo(DirectView.Guide)
            assertThat(store.read("source-b")).isEqualTo(DirectView.Channels)
        }
    }

    @Test
    fun `a source that was never opened reads nothing`() = runTest {
        open { store, _ -> assertThat(store.read("source-a")).isNull() }
    }

    @Test
    fun `the view is still there after the process is gone`() = runTest {
        val file = folder.newFile("direct_view.preferences_pb").also { it.delete() }

        open(file) { store, _ -> store.write("source-a", DirectView.Guide) }
        open(file) { store, _ -> assertThat(store.read("source-a")).isEqualTo(DirectView.Guide) }
    }

    @Test
    fun `only the view is persisted, never a search or a filter`() = runTest {
        open { store, preferences ->
            store.write("source-a", DirectView.Guide)

            val stored = preferences.data.first().asMap()
            assertThat(stored.keys.map { it.name })
                .containsExactly("direct_view_source-a")
        }
    }

    @Test
    fun `a stored value this build does not know degrades to the default`() = runTest {
        val file = folder.newFile("direct_view.preferences_pb").also { it.delete() }

        // A file written by a newer build, or a corrupted entry. Written raw, the
        // way such a file would reach this one.
        open(file) { _, preferences ->
            preferences.edit { it[stringPreferencesKey("direct_view_source-a")] = "Theatre" }
        }

        open(file) { store, _ ->
            assertThat(store.read("source-a")).isEqualTo(DirectView.Channels)
        }
    }

    /**
     * One DataStore over [file], closed before returning.
     *
     * Closed by cancelling its scope and **waiting**: DataStore refuses a second
     * instance over a file the first one still holds, which is the production
     * rule too and the reason the real one is a singleton.
     *
     * Over the Okio storage, and not the `java.io.File` one the application uses.
     * The `File` storage replaces its file with `renameTo`, which cannot overwrite
     * on Windows — so every second write fails on a developer's Windows machine
     * while passing on Android and on the Linux CI. The Okio one moves atomically
     * everywhere. What is under test is the key scheme, which sits above the
     * storage and is the same over both.
     */
    private suspend fun open(
        file: File = File(folder.root, "direct_view.preferences_pb"),
        block: suspend (DataStoreDirectViewStore, DataStore<Preferences>) -> Unit,
    ) {
        val job = Job()
        val dataStore = DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        try {
            block(DataStoreDirectViewStore(dataStore), dataStore)
        } finally {
            job.cancelAndJoin()
        }
    }
}
