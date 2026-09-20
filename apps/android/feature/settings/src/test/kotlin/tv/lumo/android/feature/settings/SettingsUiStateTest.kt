package tv.lumo.android.feature.settings

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * The readings the M6 screen makes of its state.
 *
 * The account-wide automatic-refresh switch used to be held here, because it had
 * to say something honest about a mixed account. It is gone with its problem:
 * `auto_sync` is set per source, in "My sources" (US-024), and that rule is
 * tested where it lives — `MySourcesStateTest`.
 */
class SettingsUiStateTest {

    @Test
    fun `the sources section counts and opens, and sets nothing`() {
        // US-024: automatic refresh and "refresh now" left this screen for the
        // per-source controls of "My sources". What is left here is a number —
        // and an unknown number stays unknown rather than becoming a zero.
        assertThat(SettingsUiState().sourceCount).isNull()
        assertThat(SettingsUiState(sourceCount = 2, sources = listOf(source(), source())).sources)
            .hasSize(2)
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

    private fun source(autoSync: Boolean = true) = Source(
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
