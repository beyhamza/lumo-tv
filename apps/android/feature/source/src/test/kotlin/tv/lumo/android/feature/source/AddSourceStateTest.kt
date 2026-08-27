package tv.lumo.android.feature.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.network.generated.model.SourceKind

/**
 * The empty-form gate, and only that.
 *
 * What this deliberately does **not** check is whether an address looks like an
 * address. The contract normalises an Xtream host — with or without a scheme,
 * with or without a port, with or without a trailing slash — and asks clients to
 * tolerate rather than reject (US-06). A rule here would refuse what the server
 * accepts, and the user would have no way of knowing which of the two was wrong.
 */
class AddSourceStateTest {

    @Test
    fun `an Xtream source needs its three fields, and a name`() {
        val filled = AddSourceState(
            step = AddSourceStep.Filling(SourceKind.XTREAM),
            label = "My provider",
            host = "panel.example.test",
            username = "someone",
            password = "secret",
        )

        assertThat(filled.canSubmit).isTrue()
        assertThat(filled.copy(password = "").canSubmit).isFalse()
        assertThat(filled.copy(username = " ").canSubmit).isFalse()
        assertThat(filled.copy(label = "").canSubmit).isFalse()
    }

    @Test
    fun `a bare host with no scheme and no port is accepted, because the server normalises it`() {
        val base = AddSourceState(
            step = AddSourceStep.Filling(SourceKind.XTREAM),
            label = "My provider",
            username = "someone",
            password = "secret",
        )

        // All four shapes the contract promises to accept. Refusing any of them
        // here would be this screen contradicting the server.
        listOf(
            "panel.example.test",
            "http://panel.example.test",
            "panel.example.test:8080",
            "https://panel.example.test:8080/",
        ).forEach { host ->
            assertThat(base.copy(host = host).canSubmit).isTrue()
        }
    }

    @Test
    fun `an M3U source needs an address, and does not ask for credentials`() {
        val filled = AddSourceState(
            step = AddSourceStep.Filling(SourceKind.M3U_URL),
            label = "My playlist",
            playlistUrl = "https://provider.example.test/list.m3u",
        )

        assertThat(filled.canSubmit).isTrue()
        assertThat(filled.copy(playlistUrl = "").canSubmit).isFalse()
    }

    @Test
    fun `the guide address is optional on both kinds`() {
        val m3u = AddSourceState(
            step = AddSourceStep.Filling(SourceKind.M3U_URL),
            label = "My playlist",
            playlistUrl = "https://provider.example.test/list.m3u",
        )

        assertThat(m3u.canSubmit).isTrue()
        assertThat(m3u.copy(epgUrl = "https://provider.example.test/epg.xml").canSubmit).isTrue()
    }

    @Test
    fun `nothing can be sent twice, or before a kind is chosen`() {
        val filled = AddSourceState(
            step = AddSourceStep.Filling(SourceKind.M3U_URL),
            label = "My playlist",
            playlistUrl = "https://provider.example.test/list.m3u",
        )

        assertThat(filled.copy(submitting = true).canSubmit).isFalse()
        assertThat(filled.copy(step = AddSourceStep.ChoosingKind).canSubmit).isFalse()
    }
}
