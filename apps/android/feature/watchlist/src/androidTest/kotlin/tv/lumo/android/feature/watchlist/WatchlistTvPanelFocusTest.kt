package tv.lumo.android.feature.watchlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.designsystem.theme.LumoTvTheme
import tv.lumo.android.feature.watchlist.navigation.WatchlistActions

/**
 * S11-04, WL-03 on a television: where the focus goes when the compact panel of
 * an unavailable card closes.
 *
 * - closed without removing: back on the card that opened it;
 * - after a removal: on the neighbour that took its place;
 * - after removing the last card: on the section title.
 *
 * A JVM test cannot see where the focus system seats the focus, so this drives
 * [WatchlistTvContent] over a plain state on the device's panel. The state is
 * changed the way the view model changes it.
 */
@RunWith(AndroidJUnit4::class)
class WatchlistTvPanelFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val remove get() = context.getString(R.string.feature_watchlist_remove)
    private val close get() = context.getString(R.string.feature_watchlist_close)

    private var state by mutableStateOf(WatchlistState())

    @Test
    fun closingThePanelReturnsTheFocusToTheCardThatOpenedIt() {
        launch(listOf(entry("a"), entry("gone", available = false), entry("c")))

        openPanelOn("gone")
        compose.onNodeWithText(remove).assertIsFocused()

        compose.onNodeWithText(close).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(title("gone"), substring = true).assertIsFocused()
    }

    @Test
    fun aRemovalPutsTheFocusOnTheNeighbourThatTookItsPlace() {
        launch(listOf(entry("a"), entry("gone", available = false), entry("c")))

        openPanelOn("gone")
        compose.onNodeWithText(remove).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(title("c"), substring = true).assertIsFocused()
    }

    @Test
    fun removingTheLastCardPutsTheFocusOnTheSectionTitle() {
        launch(listOf(entry("gone", available = false)))

        openPanelOn("gone")
        compose.onNodeWithText(remove).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TITLE_TAG).assertIsFocused()
    }

    // ---- helpers -------------------------------------------------------------

    private fun launch(items: List<WatchlistEntry>) {
        state = WatchlistState(
            sourceId = "source-1",
            phase = WatchlistPhase.Ready,
            items = items,
            types = items.mapTo(HashSet()) { it.type },
            loadedPages = 1,
        )
        compose.setContent {
            LumoTvTheme {
                WatchlistTvContent(
                    state = state,
                    actions = WatchlistActions({}, {}, {}, {}),
                    onRetry = {},
                    onLoadMore = {},
                    onSelectFilter = {},
                    onOpenPanel = { entry -> state = state.copy(panel = entry) },
                    onClosePanel = { state = state.copy(panel = null) },
                    onRemove = { entry ->
                        state = state.copy(
                            items = state.items.filterNot { it.id == entry.id },
                            panel = null,
                            removedId = entry.id,
                        )
                    },
                    onRemovalShown = { state = state.copy(removedId = null) },
                )
            }
        }
        compose.waitForIdle()
    }

    /** Puts the remote on the card, then presses it — as the D-pad would. */
    private fun openPanelOn(id: String) {
        val card = compose.onNodeWithText(title(id), substring = true)
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        card.performClick()
        compose.waitForIdle()
    }

    private fun title(id: String) = "Titre $id"

    private fun entry(id: String, available: Boolean = true) = WatchlistEntry(
        id = id,
        sourceId = "source-1",
        type = WatchlistType.Film,
        contentId = "content-$id".takeIf { available },
        available = available,
        title = title(id),
        posterUrl = null,
        addedAt = Instant.EPOCH,
    )
}
