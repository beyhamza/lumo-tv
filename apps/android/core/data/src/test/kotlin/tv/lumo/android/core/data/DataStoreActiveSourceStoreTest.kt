package tv.lumo.android.core.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.lumo.android.core.data.internal.DataStoreActiveSourceStore

/**
 * The file behind the active source, read and written for real (US-018).
 *
 * A Preferences DataStore runs on a plain JVM, so this is the actual
 * implementation over an actual file — no emulator, which is the rule
 * `apps/android/AGENTS.md` §8 sets for every test in this project.
 *
 * What it guards is the key. The in-memory fake the repository tests use is keyed
 * by account *by construction*; only this test says the real one is too, and a
 * single shared key would pass every other test in the module while handing one
 * person's choice to the next person who signs in on the same television.
 */
class DataStoreActiveSourceStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `each account has its own entry`() = runTest {
        open { store ->
            store.write("user-1", "source-a")
            store.write("user-2", "source-b")

            assertThat(store.read("user-1")).isEqualTo("source-a")
            assertThat(store.read("user-2")).isEqualTo("source-b")

            // Clearing one account's choice — its source was deleted — is not
            // allowed to cost the other account theirs.
            store.clear("user-1")

            assertThat(store.read("user-1")).isNull()
            assertThat(store.read("user-2")).isEqualTo("source-b")
        }
    }

    @Test
    fun `an account that never chose reads nothing`() = runTest {
        open { store -> assertThat(store.read("user-1")).isNull() }
    }

    @Test
    fun `the choice is still there after the process is gone`() = runTest {
        val file = folder.newFile("active_source.preferences_pb").also { it.delete() }

        open(file) { store -> store.write("user-1", "source-a") }
        open(file) { store -> assertThat(store.read("user-1")).isEqualTo("source-a") }
    }

    /**
     * One DataStore over [file], closed before returning.
     *
     * Closed by cancelling its scope and **waiting**: DataStore refuses a second
     * instance over a file the first one still holds, which is the production
     * rule too and the reason the real one is a singleton.
     *
     * Over the Okio storage, and not the `java.io.File` one the application
     * uses. The `File` storage replaces its file with `renameTo`,
     * which cannot overwrite on Windows — so every second write fails on a
     * developer's Windows machine while passing on Android and on the Linux CI.
     * The Okio one moves atomically everywhere. What is under test is the key
     * scheme, which sits above the storage and is the same over both.
     */
    private suspend fun open(
        file: File = File(folder.root, "active_source.preferences_pb"),
        block: suspend (DataStoreActiveSourceStore) -> Unit,
    ) {
        val job = Job()
        val dataStore = DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        try {
            block(DataStoreActiveSourceStore(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }
}
