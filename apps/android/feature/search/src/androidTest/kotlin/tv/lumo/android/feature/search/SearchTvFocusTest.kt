package tv.lumo.android.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.CataloguePresence
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.SearchResults
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.SearchRepository
import tv.lumo.android.core.designsystem.theme.LumoTvTheme
import tv.lumo.android.feature.search.R as FeatureSearchR

/**
 * S10-05, SR-13, on a television: inside the search, the field must not be a
 * D-pad dead end.
 *
 * <p>A `BasicTextField` keeps UP and DOWN for its own cursor and never lets the
 * focus walk out. QA measured it on the 1080p panel: with the keyboard closed,
 * no direction left the field, so the filter tabs, *See results* and the *Clear
 * the search* button of the no-result state were unreachable with a remote (only
 * TAB worked). This test drives the real [SearchTvScreen] over a real
 * [SearchViewModel] whose repository answers "nothing matched", and pins the
 * journey: DOWN reaches the first filter, DOWN again reaches *Clear the search*,
 * UP comes back, and OK on *Clear the search* empties the query and returns the
 * focus to the field.
 *
 * <p>Only the D-pad is exercised here: a JVM test cannot observe where the focus
 * system seats a key press.
 */
@RunWith(AndroidJUnit4::class)
class SearchTvFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val hint = context.getString(FeatureSearchR.string.feature_search_hint)
    private val allFilter = context.getString(FeatureSearchR.string.feature_search_filter_all)
    private val clear = context.getString(FeatureSearchR.string.feature_search_clear)
    private val invitation = context.getString(FeatureSearchR.string.feature_search_invitation)

    /** The screen's engine, kept so a test can submit or observe it. */
    private lateinit var viewModel: SearchViewModel

    /** Arrival seats the focus on the field and nothing else (SR-13). */
    @Test
    fun onArrivalOnlyTheFieldHasTheFocus() {
        launch()

        compose.onNodeWithContentDescription(hint).assertIsFocused()
    }

    /**
     * The red of the fix: DOWN used to be swallowed by the field. It must now
     * reach the first filter tab.
     */
    @Test
    fun downFromTheFieldReachesTheFirstFilterTab() {
        launch()
        typeNoResult()

        compose.onNodeWithContentDescription(hint).assertIsFocused()
        compose.onNodeWithContentDescription(hint).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        compose.onNodeWithText(allFilter).assertIsFocused()
    }

    /**
     * From the first filter, DOWN reaches *Clear the search* — the one control of
     * the no-result state, which a remote previously could not reach at all.
     */
    @Test
    fun downFromTheFirstFilterReachesClearTheSearch() {
        launch()
        typeNoResult()

        compose.onNodeWithContentDescription(hint).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText(allFilter).assertIsFocused()

        compose.onNodeWithText(allFilter).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        compose.onNodeWithText(clear).assertIsFocused()
    }

    /** UP from the first filter comes back to the field. */
    @Test
    fun upFromTheFirstFilterReturnsToTheField() {
        launch()
        typeNoResult()

        compose.onNodeWithContentDescription(hint).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText(allFilter).assertIsFocused()

        compose.onNodeWithText(allFilter).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(hint).assertIsFocused()
    }

    /**
     * OK on *Clear the search* empties the query and puts the focus back on the
     * field, so the D-pad can type again without a detour.
     */
    @Test
    fun okOnClearEmptiesTheQueryAndReturnsToTheField() {
        launch()
        typeNoResult()

        compose.onNodeWithContentDescription(hint).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText(allFilter).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText(clear).assertIsFocused()

        compose.onNodeWithText(clear).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(hint).assertIsFocused()
        compose.onNodeWithText(invitation).assertIsDisplayed()
        compose.onAllNodesWithText(clear).assertCountEquals(0)
    }

    /** Runs the real screen over a view model whose three sections answer empty. */
    private fun launch() {
        val engine = SearchViewModel(EmptySearchRepository(), FakeActiveSource())
        viewModel = engine
        compose.setContent {
            LumoTvTheme {
                SearchTvScreen(
                    onPlayChannel = { _, _ -> },
                    onOpenFilm = {},
                    onOpenSeries = {},
                    viewModel = engine,
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Puts the screen in the completed global no-result state.
     *
     * The text is set on the engine rather than typed: the question here is where
     * the D-pad seats the focus, and a `performTextInput` would open the platform
     * IME, whose closing commit re-schedules the search and puts the engine back
     * on the virtual clock. The query itself is the unit suite's subject.
     */
    private fun typeNoResult() {
        compose.runOnIdle {
            viewModel.onQueryChanged("zz", composing = false)
            viewModel.onSearchSubmitted()
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(clear).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Every requested section says "nothing matched" — the global no-result state. */
    private class EmptySearchRepository : SearchRepository {

        override suspend fun search(
            sourceId: String,
            query: String,
            filter: SearchFilter,
            page: Int,
            size: Int,
            present: CataloguePresence,
        ): SearchResults = SearchResults(
            channels = present.channels.takeIf { filter.includes(SearchFilter.Channels) }
                ?.let { LumoResult.Success(SearchPage(emptyList(), 0)) },
            films = present.films.takeIf { filter.includes(SearchFilter.Films) }
                ?.let { LumoResult.Success(SearchPage(emptyList(), 0)) },
            series = present.series.takeIf { filter.includes(SearchFilter.Series) }
                ?.let { LumoResult.Success(SearchPage(emptyList(), 0)) },
        )

        override suspend fun cataloguePresence(sourceId: String): CataloguePresence = CataloguePresence.all
    }

    /** One selected source, signed in; the screen only needs its identifier. */
    private class FakeActiveSource : ActiveSourceRepository {

        private val mutable = MutableStateFlow<ActiveSourceState>(
            ActiveSourceState.Selected(sourceId = "s1", source = null, sources = emptyList()),
        )

        override val state: StateFlow<ActiveSourceState> = mutable.asStateFlow()

        override val accountId: Flow<String?> = flowOf("acc")

        override suspend fun select(sourceId: String) = Unit

        override suspend fun refresh() = Unit

        override suspend fun onSourceGone(sourceId: String) = Unit
    }
}
