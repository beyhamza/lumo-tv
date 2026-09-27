package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.core.data.EpgDayWindow
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.designsystem.component.LumoTvStateMessage
import tv.lumo.android.core.designsystem.component.LumoTvButton
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvTheme
import tv.lumo.android.core.designsystem.tv.tvOverscan
import tv.lumo.android.feature.live.R as FeatureLiveR

/**
 * GD-10 on a real television: the Guide's **initial error** (a failed read with no
 * data) must actually paint — title, body and both actions — inside the area the
 * Guide leaves under its search field.
 *
 * <p>This is the instrumented half of `GuideStatesTest`. The pure state machine
 * already says the day is `InitialError`; what QA measured on the 1080p panel is
 * that the accessibility tree was alive (title, *Try again*, *See channels*, the
 * retry fires a request) while **nothing was drawn**: the message title laid out
 * 2 px tall, because the message's own 48 dp padding ate the ~105 dp the two-row
 * day-tab header left it. `BUG-S9-06-03-01`.
 *
 * <p>The harness reproduces the Live TV column faithfully — the panel's overscan,
 * the screen padding, the title row, the search field above the state, the
 * key-hint line below — so the state is measured under the same height budget as
 * on the panel. A bare `setContent { ... }` would give it the whole window and
 * hide the defect.
 */
@RunWith(AndroidJUnit4::class)
class GuideInitialErrorTvTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val title = context.getString(FeatureLiveR.string.feature_live_guide_error_title)
    private val body = context.getString(FeatureLiveR.string.feature_live_guide_error_body)
    private val retry = context.getString(FeatureLiveR.string.feature_live_guide_retry)
    private val seeChannels = context.getString(FeatureLiveR.string.feature_live_guide_see_channels)

    /** The rail's first entry, in the harness, so the focus starts where a viewer's does. */
    private val railLabel = "Rail"

    private val emptySearchTitle = context.getString(FeatureLiveR.string.feature_live_search_empty_title)
    private val emptySearchBody =
        context.getString(FeatureLiveR.string.feature_live_search_empty_body, "zzqx")
    private val emptySearchClear = context.getString(FeatureLiveR.string.feature_live_search_clear)
    private val emptySearchAll = context.getString(FeatureLiveR.string.feature_live_all_categories)

    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val days = EpgDayWindow.around(now, zone)
    private val today = days[EpgDayWindow.DAYS_BEFORE.toInt()]

    private val channel = Channel(
        id = "c1",
        sourceId = "s1",
        categoryId = null,
        name = "Chaîne 1",
        logoUrl = null,
        number = 1,
        quality = "HD",
        isAdult = false,
    )

    /** A failed read with nothing cached: `guideStateOf` returns `InitialError`. */
    private val initialError = LiveState(
        step = LiveStep.Browsing,
        sourceId = "s1",
        view = DirectView.Guide,
        guideDay = GuideDay(
            day = today,
            configured = true,
            status = GuideStatus(read = GuideRead.Failed(LumoError.Offline(java.io.IOException("no route")))),
        ),
    )

    /** The Live TV shell, down to the heights the panel really gives each band. */
    @Composable
    private fun LiveTvColumn(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The real shell paints the Ink canvas (LiveTvScreen); the harness
                // did not, so a capture would have read as light text on white.
                .background(LumoColors.Ink)
                .tvOverscan()
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TvText(text = "Live TV", style = TvMaterialTheme.typography.displayMedium)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                ) {
                    // The search field above the Guide or the channel grid (S9-04).
                    Box(modifier = Modifier.fillMaxWidth().height(48.dp))
                    content()
                }
            }

            TvText(
                text = context.getString(FeatureLiveR.string.feature_live_tv_grid_hints),
                style = TvMaterialTheme.typography.labelLarge,
            )
        }
    }

    @Test
    fun theInitialErrorScreenPaintsItsTitleBodyAndBothActions() {
        compose.setContent {
            LumoTvTheme {
                LiveTvColumn {
                    val channels = flowOf(PagingData.from(listOf(channel)))
                        .collectAsLazyPagingItems()
                    GuideGridTv(
                        state = initialError,
                        channels = channels,
                        days = days,
                        activeDay = today,
                        today = today.date,
                        now = now,
                        onNow = {},
                        onSelectDay = {},
                        onOpenProgramme = { _, _, _ -> },
                        onDayVisible = { _, _ -> },
                        onAnchorChanged = {},
                        onSeeChannels = {},
                        onRetryGuide = {},
                    )
                }
            }
        }

        assertPaints(
            "title" to title to 40.dp,
            "body" to body to 16.dp,
            "retry" to retry to 40.dp,
            "see channels" to seeChannels to 40.dp,
        )
    }

    /**
     * The same `LumoTvStateMessage` in the channel view, where QA measured the
     * title and body painted but the action row clipped: the message was taller
     * than the area under the search field because of the same padding.
     */
    @Test
    fun theSearchEmptyActionsPaintInTheChannelsArea() {
        compose.setContent {
            LumoTvTheme {
                LiveTvColumn {
                    LumoTvStateMessage(
                        title = emptySearchTitle,
                        body = emptySearchBody,
                        actionLabel = emptySearchClear,
                        onAction = {},
                        secondaryActionLabel = emptySearchAll,
                        onSecondaryAction = {},
                    )
                }
            }
        }

        assertPaints(
            "title" to emptySearchTitle to 40.dp,
            "body" to emptySearchBody to 16.dp,
            "clear" to emptySearchClear to 40.dp,
            "all" to emptySearchAll to 40.dp,
        )
    }

    /**
     * The real shell on the Guide's initial error: the rail on the left, the Live
     * TV column on the right. On an ordinary Guide day the header also carries the
     * focusable view toggle, which put a stop between the rail and the message; on
     * the initial error it is hidden (BUG-S9-06-03-02 s fix), so this harness
     * mirrors that and the focus path is the real one.
     */
    @Composable
    private fun LiveTvShellWithRail(content: @Composable () -> Unit) {
        val railRequester = remember { FocusRequester() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Ink)
                .tvOverscan()
                .padding(LumoSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
        ) {
            LumoTvButton(text = railLabel, onClick = {}, focusRequester = railRequester)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TvText(text = "Live TV", style = TvMaterialTheme.typography.displayMedium)
                }
                // The search field is hidden on the initial error, exactly as
                // LiveTvScreen does: the message gets the band directly.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
                ) {
                    content()
                }
            }
        }
        LaunchedEffect(Unit) { railRequester.requestFocus() }
    }

    @Composable
    private fun GuideInitialError(channels: androidx.paging.compose.LazyPagingItems<Channel>) {
        GuideGridTv(
            state = initialError,
            channels = channels,
            days = days,
            activeDay = today,
            today = today.date,
            now = now,
            onNow = {},
            onSelectDay = {},
            onOpenProgramme = { _, _, _ -> },
            onDayVisible = { _, _ -> },
            onAnchorChanged = {},
            onSeeChannels = {},
            onRetryGuide = {},
        )
    }

    /**
     * Rule 1 of `tv-focus-map.md`: a single `RIGHT` from the rail reaches the
     * primary action. Before BUG-S9-06-03-02 the view toggle sat in the way and
     * the viewer needed three `RIGHT` and a `DOWN` to get to *See channels*.
     */
    @Test
    fun oneRightFromTheRailReachesTheRetryAction() {
        compose.setContent {
            LumoTvTheme {
                LiveTvShellWithRail {
                    val channels = flowOf(PagingData.from(listOf(channel)))
                        .collectAsLazyPagingItems()
                    GuideInitialError(channels)
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText(railLabel).assertIsFocused()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()

        compose.onNodeWithText(retry).assertIsFocused()
    }

    /**
     * A retry whose read fails again disposes the focused button while the screen
     * goes through `Loading`; the focus used to fall back to the rail (GD-10,
     * "focus conservés"). It must come back on the action the viewer pressed.
     */
    @Test
    fun aRetryThatFallsBackToTheInitialErrorKeepsTheFocusOnRetry() {
        compose.setContent {
            LumoTvTheme {
                // The read failing again: a moment of Loading, then the same
                // initial error, which is what dropped the focus to the rail.
                val loading = initialError.copy(
                    guideDay = initialError.guideDay.copy(status = GuideStatus(read = GuideRead.Idle)),
                )
                var state by remember { mutableStateOf(initialError) }
                LaunchedEffect(state) {
                    if (state.guideState() == GuideState.Loading) {
                        withFrameNanos { }
                        state = initialError
                    }
                }
                val channels = flowOf(PagingData.from(listOf(channel)))
                    .collectAsLazyPagingItems()
                LiveTvShellWithRail {
                    GuideGridTv(
                        state = state,
                        channels = channels,
                        days = days,
                        activeDay = today,
                        today = today.date,
                        now = now,
                        onNow = {},
                        onSelectDay = {},
                        onOpenProgramme = { _, _, _ -> },
                        onDayVisible = { _, _ -> },
                        onAnchorChanged = {},
                        onSeeChannels = {},
                        onRetryGuide = { state = loading },
                    )
                }
            }
        }

        compose.waitForIdle()
        // The viewer sits on Réessayer, as QA did, then presses it.
        compose.onNodeWithText(retry).requestFocus()
        compose.waitForIdle()
        compose.onNodeWithText(retry).assertIsFocused()

        compose.onNodeWithText(retry).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(retry).assertIsFocused() }.isSuccess
        }
        compose.onNodeWithText(retry).assertIsFocused()
    }

    /** Every named node must be laid out tall enough to be drawn, then displayed. */
    private fun assertPaints(vararg nodes: Pair<Pair<String, String>, Dp>) {
        val failures = mutableListOf<String>()
        nodes.forEach { (named, minimum) ->
            val (name, text) = named
            runCatching { compose.onNodeWithText(text).assertHeightIsAtLeast(minimum) }
                .onFailure { failures += "$name (layout): ${it.message}" }
        }
        nodes.forEach { (named, _) ->
            val (name, text) = named
            runCatching { compose.onNodeWithText(text).assertIsDisplayed() }
                .onFailure { failures += "$name (displayed): ${it.message}" }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
