package tv.lumo.android.feature.settings

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.network.generated.model.Device
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Platform
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * The readings the settings screens make of their state (US-025, S8-06).
 *
 * Everything with a right and a wrong answer is a pure function of
 * [SettingsUiState], so it is pinned here without a session or a server: which
 * device is this one, what a confirmation does, and what a refused revoke leaves
 * behind — the device, and the question.
 *
 * The account-wide automatic-refresh switch used to be held here, because it had
 * to say something honest about a mixed account. It is gone with its problem:
 * `auto_sync` is set per source, in "My sources" (US-024), and that rule is
 * tested where it lives — `MySourcesStateTest`.
 */
class SettingsUiStateTest {

    private val here = device(current = true, model = "Bench phone")
    private val there = device(current = false, name = "Living room TV", platform = Platform.ANDROID_TV)
    private val web = device(current = false, platform = Platform.WEB, lastSeen = null)

    // ---- sections -----------------------------------------------------------

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
    fun `this device is the row the server marked, and the others keep the server's order`() {
        val state = SettingsUiState(devices = DevicesState.Loaded(listOf(there, here, web)))

        // `is_current` is computed against the token that made the call: the
        // screen marks that row and never guesses it from the platform.
        assertThat(state.currentDevice).isEqualTo(here)
        // Most recently seen first, as `GET /me/devices` orders them.
        assertThat(state.otherDevices).containsExactly(there, web).inOrder()
    }

    @Test
    fun `a list that could not be read is not an empty one`() {
        // The design asks for a retry there, and "no other device" would be a
        // claim nobody verified.
        assertThat(SettingsUiState(devices = DevicesState.Unavailable).otherDevices).isEmpty()
        assertThat(SettingsUiState(devices = DevicesState.Unavailable).currentDevice).isNull()
        assertThat(SettingsUiState().devices).isEqualTo(DevicesState.Loading)
    }

    @Test
    fun `a device is called by its name, else its model, else its platform`() {
        assertThat(there.givenTitle()).isEqualTo("Living room TV")
        assertThat(here.givenTitle()).isEqualTo("Bench phone")
        // Nothing given: the screen falls back to the platform's own label.
        assertThat(web.givenTitle()).isNull()
        assertThat(device(current = false, name = "  ").givenTitle()).isNull()
        assertThat(Platform.WEB.labelRes()).isEqualTo(R.string.feature_settings_platform_web)
        assertThat(Platform.ANDROID_TV.labelRes()).isEqualTo(R.string.feature_settings_platform_android_tv)
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

    // ---- confirmations --------------------------------------------------------

    @Test
    fun `signing out asks first, and cancelling changes nothing`() {
        val asked = SettingsUiState(signedIn = true).askSignOut()

        assertThat(asked.confirmation).isEqualTo(SettingsConfirmation.SignOut)
        assertThat(asked.signingOut).isFalse()
        assertThat(asked.dismissConfirmation().confirmation).isNull()
    }

    @Test
    fun `one question at a time, and none while signing out`() {
        val signOut = SettingsUiState().askSignOut()

        // A second question over the first would be a state with no drawing.
        assertThat(signOut.askRevoke(there).confirmation).isEqualTo(SettingsConfirmation.SignOut)
        assertThat(SettingsUiState(signingOut = true).askSignOut().confirmation).isNull()
    }

    @Test
    fun `revoking names the device and keeps it until the server agrees`() {
        val loaded = SettingsUiState(devices = DevicesState.Loaded(listOf(here, there)))
        val asked = loaded.askRevoke(there)

        assertThat(asked.confirmation).isEqualTo(SettingsConfirmation.Revoke(there))
        // On the wire: the device is still listed, the question is busy.
        val busy = asked.revoking()
        assertThat(busy.otherDevices).containsExactly(there)
        assertThat((busy.confirmation as SettingsConfirmation.Revoke).busy).isTrue()
        // ...and cannot be dismissed from under the call.
        assertThat(busy.dismissConfirmation().confirmation).isEqualTo(busy.confirmation)

        // The server agreed: the row goes, the question closes.
        val done = busy.revoked(there.id.toString())
        assertThat(done.confirmation).isNull()
        assertThat(done.otherDevices).isEmpty()
        assertThat(done.currentDevice).isEqualTo(here)
    }

    @Test
    fun `a refused revoke keeps the device and the question, with the reason under it`() {
        val busy = SettingsUiState(devices = DevicesState.Loaded(listOf(here, there)))
            .askRevoke(there)
            .revoking()
        val offline = LumoError.Offline(IOException("no route"))

        val refused = busy.revokeRefused(offline)

        // docs/design/0.2.0/settings.md: on a failure, keep the device and offer
        // a retry. A row that disappeared would be a success the screen made up.
        assertThat(refused.otherDevices).containsExactly(there)
        assertThat(refused.confirmation)
            .isEqualTo(SettingsConfirmation.Revoke(there, busy = false, failure = offline))
        // The retry starts clean: a stale reason under a new attempt would lie.
        assertThat((refused.revoking().confirmation as SettingsConfirmation.Revoke).failure).isNull()
        // The question can be closed once the call is over.
        assertThat(refused.dismissConfirmation().confirmation).isNull()
    }

    @Test
    fun `a revoke outcome without a question open changes nothing`() {
        val quiet = SettingsUiState(devices = DevicesState.Loaded(listOf(here, there)))

        assertThat(quiet.revoking()).isEqualTo(quiet)
        assertThat(quiet.revokeRefused(LumoError.Api(ErrorCode.DEVICE_NOT_FOUND, detail = null)))
            .isEqualTo(quiet)
    }

    @Test
    fun `signing out drops everything read after the session, and keeps the build's facts`() {
        val full = SettingsUiState(
            signedIn = true,
            email = "someone@example.test",
            displayName = "Someone",
            sourceCount = 1,
            sources = listOf(source()),
            devices = DevicesState.Loaded(listOf(here)),
            activationUrl = "https://lumo.tv/activate",
            appVersion = "0.1.0",
            confirmation = SettingsConfirmation.SignOut,
        )

        assertThat(full.signedOut()).isEqualTo(
            SettingsUiState(activationUrl = "https://lumo.tv/activate", appVersion = "0.1.0"),
        )
    }

    // ---- fixtures -------------------------------------------------------------

    private fun device(
        current: Boolean,
        name: String? = null,
        model: String? = null,
        platform: Platform = Platform.ANDROID_MOBILE,
        lastSeen: OffsetDateTime? = OffsetDateTime.parse("2026-09-20T10:00:00Z"),
    ) = Device(
        id = UUID.randomUUID(),
        platform = platform,
        isCurrent = current,
        createdAt = OffsetDateTime.parse("2026-09-01T10:00:00Z"),
        name = name,
        model = model,
        appVersion = "0.1.0",
        lastSeenAt = lastSeen,
    )

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
