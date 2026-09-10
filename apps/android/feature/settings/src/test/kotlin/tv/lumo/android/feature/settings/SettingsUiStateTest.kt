package tv.lumo.android.feature.settings

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * The three readings the M6 screen makes of its state, and the one that is
 * easy to get wrong: `auto_sync` is per source, and the account-wide switch has
 * to say something honest about a mixed account.
 */
class SettingsUiStateTest {

    @Test
    fun `the switch is off unless every source refreshes on its own`() {
        assertThat(autoSyncOf(listOf(source(autoSync = true), source(autoSync = true)))).isTrue()
        assertThat(autoSyncOf(listOf(source(autoSync = true), source(autoSync = false)))).isFalse()
    }

    @Test
    fun `no source means no switch, not a switch that is off`() {
        // Null, not false: the screen draws a disabled control, and nothing it
        // could write would change anything.
        assertThat(autoSyncOf(emptyList())).isNull()
    }

    @Test
    fun `the disc carries the first letter of the address, upper-cased`() {
        assertThat(SettingsUiState(email = "someone@example.test").initial).isEqualTo("S")
        assertThat(SettingsUiState(email = null).initial).isEmpty()
    }

    @Test
    fun `the pairing row prints the address without its scheme`() {
        val state = SettingsUiState(activationUrl = "https://lumo.tv/activate/")

        assertThat(state.activationLabel).isEqualTo("lumo.tv/activate")
        // The full URL is what the browser is handed; the label is only what is read.
        assertThat(state.activationUrl).startsWith("https://")
    }

    @Test
    fun `the session line still maps the way both surfaces expect`() {
        assertThat(sessionLabelOf(SettingsUiState(signedIn = true)))
            .isEqualTo(R.string.feature_settings_session_open)
        assertThat(sessionLabelOf(SettingsUiState(signedIn = false)))
            .isEqualTo(R.string.feature_settings_session_none)
    }

    private fun source(autoSync: Boolean) = Source(
        id = UUID.randomUUID(),
        label = "Test source",
        kind = SourceKind.M3U_URL,
        status = SourceStatus.READY,
        autoSync = autoSync,
        // A rights-free test stream host, never a real provider (AGENTS.md §1).
        m3uUrl = "https://example.test/playlist.m3u",
        lastSyncedAt = OffsetDateTime.now(),
    )
}
