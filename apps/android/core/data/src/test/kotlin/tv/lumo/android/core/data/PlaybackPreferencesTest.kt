package tv.lumo.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.lumo.android.core.common.playback.TrackPreferences
import tv.lumo.android.core.data.repository.DataStorePlaybackPreferences

/**
 * S13-02/03/05 and Q6: playback preferences, per account on this device, over
 * a real DataStore file (Okio storage, for the reason `DirectViewStoreTest` gives).
 */
class PlaybackPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val account = MutableStateFlow<String?>("account-a")
    private var interfaceLanguage = "fr"

    @Test
    fun `first use seeds both languages from the interface, subtitles off (Q6)`() = runTest {
        open { preferences ->
            assertThat(preferences.trackPreferences())
                .isEqualTo(TrackPreferences(audioLanguage = "fr", subtitlesEnabled = false, subtitleLanguage = "fr"))
        }
    }

    @Test
    fun `changing the interface language afterwards moves nothing (PS-01)`() = runTest {
        open { preferences ->
            preferences.trackPreferences()
            interfaceLanguage = "en"

            assertThat(preferences.trackPreferences().audioLanguage).isEqualTo("fr")
        }
    }

    @Test
    fun `accounts A, B, A on one device keep their own languages (PS-02)`() = runTest {
        open { preferences ->
            preferences.saveTrackPreferences(TrackPreferences("deu", true, "deu"))

            account.value = "account-b"
            assertThat(preferences.trackPreferences().audioLanguage).isEqualTo("fr")
            preferences.saveTrackPreferences(TrackPreferences("eng", false, "eng"))

            account.value = "account-a"
            assertThat(preferences.trackPreferences()).isEqualTo(TrackPreferences("deu", true, "deu"))
        }
    }

    @Test
    fun `next episode is on by default and per account`() = runTest {
        open { preferences ->
            assertThat(preferences.autoplayNext()).isTrue()
            assertThat(preferences.setAutoplayNext(false)).isTrue()
            assertThat(preferences.autoplayNext()).isFalse()

            account.value = "account-b"
            assertThat(preferences.autoplayNext()).isTrue()
        }
    }

    @Test
    fun `mobile data is allowed by default and belongs to the installation (Q7)`() = runTest {
        open { preferences ->
            assertThat(preferences.mobileDataAllowed()).isTrue()
            preferences.setMobileDataAllowed(false)

            account.value = "account-b"
            assertThat(preferences.mobileDataAllowed()).isFalse()
            assertThat(preferences.settings.first().mobileDataAllowed).isFalse()
        }
    }

    @Test
    fun `settings show the seed without writing it, then what was edited (S13-04)`() = runTest {
        open { preferences ->
            // Read before any playback: the would-be seed, and nothing written.
            assertThat(preferences.settings.first().tracks)
                .isEqualTo(TrackPreferences(audioLanguage = "fr", subtitlesEnabled = false, subtitleLanguage = "fr"))
            interfaceLanguage = "en"
            assertThat(preferences.settings.first().tracks.audioLanguage).isEqualTo("en")

            // Edited in Settings → Playback: the player opens the next stream with it.
            val edited = TrackPreferences(audioLanguage = "en", subtitlesEnabled = true, subtitleLanguage = "es")
            assertThat(preferences.saveTrackPreferences(edited)).isTrue()
            assertThat(preferences.settings.first().tracks).isEqualTo(edited)
            assertThat(preferences.trackPreferences()).isEqualTo(edited)

            // And a choice made in the player is what the settings show next.
            preferences.saveTrackPreferences(edited.copy(subtitlesEnabled = false))
            assertThat(preferences.settings.first().tracks.subtitlesEnabled).isFalse()
            assertThat(preferences.settings.first().tracks.subtitleLanguage).isEqualTo("es")

            account.value = "account-b"
            assertThat(preferences.settings.first().tracks.subtitlesEnabled).isFalse()
            assertThat(preferences.settings.first().tracks.subtitleLanguage).isEqualTo("en")
        }
    }

    @Test
    fun `without an account nothing is written and the defaults are answered`() = runTest {
        account.value = null
        open { preferences ->
            assertThat(preferences.trackPreferences()).isEqualTo(TrackPreferences.NONE)
            assertThat(preferences.saveTrackPreferences(TrackPreferences("fra", true, "fra"))).isFalse()
            assertThat(preferences.setAutoplayNext(false)).isFalse()
        }
    }

    @Test
    fun `preferences survive the process`() = runTest {
        val file = File(folder.root, "playback.preferences_pb")
        open(file) { it.saveTrackPreferences(TrackPreferences("ita", true, "ita")) }
        open(file) { assertThat(it.trackPreferences()).isEqualTo(TrackPreferences("ita", true, "ita")) }
    }

    private suspend fun open(
        file: File = File(folder.root, "playback.preferences_pb"),
        block: suspend (DataStorePlaybackPreferences) -> Unit,
    ) {
        val job = Job()
        val dataStore: DataStore<Preferences> = DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        try {
            block(
                DataStorePlaybackPreferences(
                    preferences = dataStore,
                    accountId = { account.value },
                    accountIds = account,
                    interfaceLanguage = { interfaceLanguage },
                ),
            )
        } finally {
            job.cancelAndJoin()
        }
    }
}
