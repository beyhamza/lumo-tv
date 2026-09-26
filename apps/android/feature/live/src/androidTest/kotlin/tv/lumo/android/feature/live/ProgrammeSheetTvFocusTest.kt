package tv.lumo.android.feature.live

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.feature.live.R as FeatureLiveR

/**
 * GD-07 on a real television: at the programme's end the action leaves the
 * composition and the focus must join **Fermer**, not fall back to the guide
 * behind the modal panel.
 *
 * <p>This is the instrumented half of [ProgrammeSheetTest]. The pure rule
 * ([sheetFocusAfter]) already says the destination is `Close`; what these two
 * cases pin is that the panel actually *seats* the focus there, and that the
 * focus cannot walk out of the panel while the sheet is open. The unit test
 * cannot see that — it is the focus system's behaviour on a device, which is the
 * half `BUG-S9-06-01-01` was filed against.
 */
@RunWith(AndroidJUnit4::class)
class ProgrammeSheetTvFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val watchLabel = context.getString(FeatureLiveR.string.feature_live_programme_watch)
    private val closeLabel = context.getString(FeatureLiveR.string.feature_live_programme_close)

    private val programme = EpgProgramme(
        id = "p1",
        startsAt = Instant.parse("2026-09-24T20:00:00Z"),
        endsAt = Instant.parse("2026-09-24T21:00:00Z"),
        title = "Le journal",
        description = "Le résumé du jour",
        category = "Info",
    )

    private val sheet = ProgrammeSheet(
        channelId = "c1",
        channelName = "Chaîne 1",
        programme = programme,
    )

    /**
     * The red of BUG-S9-06-01-01: the action disappears at the end while it holds
     * the focus, and the panel must move the focus to Fermer — and keep it there.
     */
    @Test
    fun whenTheActionDisappears_theFocusJoinsClose() {
        val clock = mutableStateOf(Instant.parse("2026-09-24T20:30:00Z"))
        compose.setContent {
            ProgrammeSheetTv(
                sheet = sheet,
                now = clock.value,
                onClose = {},
                onWatch = {},
                onTimePassed = {},
            )
        }

        compose.onNodeWithText(watchLabel).assertIsFocused()

        // The programme ends. No fresh `Instant.now()`: the panel's own clock is
        // advanced, exactly like the screen's SheetEndTick does on a device.
        compose.runOnIdle { clock.value = Instant.parse("2026-09-24T21:00:00Z") }
        compose.waitForIdle()

        compose.onNodeWithText(closeLabel).assertIsFocused()
        compose.onNodeWithText(watchLabel).assertDoesNotExist()
    }

    /** A focus that joined Fermer must not leak back to the guide on a key. */
    @Test
    fun afterTheActionDisappears_theFocusStaysOnClose() {
        val clock = mutableStateOf(Instant.parse("2026-09-24T20:30:00Z"))
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                // A focusable "guide cell" behind the modal panel, to prove the
                // focus does not land on it.
                Box(Modifier.fillMaxSize().testTag("guide-behind").focusable())
                ProgrammeSheetTv(
                    sheet = sheet,
                    now = clock.value,
                    onClose = {},
                    onWatch = {},
                    onTimePassed = {},
                )
            }
        }

        compose.onNodeWithText(watchLabel).assertIsFocused()

        compose.runOnIdle { clock.value = Instant.parse("2026-09-24T21:00:00Z") }
        compose.waitForIdle()

        compose.onNodeWithText(closeLabel).assertIsFocused()
        compose.onNodeWithText(closeLabel).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText(closeLabel).assertIsFocused()
        compose.onNodeWithTag("guide-behind").assertIsNotFocused()
    }

    /**
     * The deferred clear QA's uiautomator dump caught: the focus system clears
     * the focus (no `focused="true"` node) after the action has left. The panel
     * must reclaim it, because the sheet is still open — that is the trap.
     */
    @Test
    fun whenTheFocusSystemClearsTheFocus_thePanelReclaimsIt() {
        val clock = mutableStateOf(Instant.parse("2026-09-24T20:30:00Z"))
        lateinit var focusManager: FocusManager
        compose.setContent {
            focusManager = LocalFocusManager.current
            ProgrammeSheetTv(
                sheet = sheet,
                now = clock.value,
                onClose = {},
                onWatch = {},
                onTimePassed = {},
            )
        }

        compose.onNodeWithText(watchLabel).assertIsFocused()

        compose.runOnIdle { clock.value = Instant.parse("2026-09-24T21:00:00Z") }
        compose.waitForIdle()
        compose.onNodeWithText(closeLabel).assertIsFocused()

        // Reproduces the deferred invalidation that leaves nothing focused.
        compose.runOnIdle { focusManager.clearFocus() }
        compose.waitForIdle()

        compose.onNodeWithText(closeLabel).assertIsFocused()
    }

    /**
     * While the sheet is open the focus is a prisoner of the panel: the four
     * D-pad directions never reach the guide cell drawn behind it.
     */
    @Test
    fun theFocusCannotEscapeThePanel() {
        val clock = mutableStateOf(Instant.parse("2026-09-24T20:30:00Z"))
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("guide-behind").focusable())
                ProgrammeSheetTv(
                    sheet = sheet,
                    now = clock.value,
                    onClose = {},
                    onWatch = {},
                    onTimePassed = {},
                )
            }
        }

        compose.onNodeWithText(watchLabel).assertIsFocused()

        compose.onNodeWithText(watchLabel).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText(watchLabel).assertIsFocused()
        compose.onNodeWithText(watchLabel).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText(watchLabel).assertIsFocused()
        compose.onNodeWithText(watchLabel).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(closeLabel).assertIsFocused()
        compose.onNodeWithText(closeLabel).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText(watchLabel).assertIsFocused()

        compose.onNodeWithTag("guide-behind").assertIsNotFocused()
    }
}
