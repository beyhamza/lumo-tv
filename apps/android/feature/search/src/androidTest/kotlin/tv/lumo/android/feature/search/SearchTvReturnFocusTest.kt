package tv.lumo.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.CataloguePresence
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.SearchFilter
import tv.lumo.android.core.data.model.SearchPage
import tv.lumo.android.core.data.model.SearchResults
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.SearchRepository
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoTvTheme

/**
 * S10-03, SR-12, on a television: a return from a fiche or a player must land
 * back on the card that was chosen, with the results still on screen.
 *
 * <p>The unit suite already pins the state half of the fix ([SearchViewModel]
 * must not empty the sections when the field replays its unchanged text). What a
 * JVM test cannot observe is where the focus system seats the return, so this
 * test drives the real [SearchTvScreen] over a real [SearchViewModel] and reads
 * the focus off the 1080p panel.
 *
 * <h2>How the return is reproduced</h2>
 *
 * A real return is "the destination left the composition, then came back", and
 * that is exactly what a `NavHost` does with `SaveableStateProvider`: the entry's
 * `rememberSaveable` values — [SearchTvScreen]'s `chosen` marker among them — are
 * saved when the destination is removed and handed back on return. This test
 * wraps the screen in a [rememberSaveableStateHolder] and flips a flag to model
 * the same leave/return, without an activity recreation (which would also drag
 * the framework's own focus restore into the picture).
 *
 * <p>The replay of the unchanged text is part of the scenario, not a shortcut:
 * when the field regains focus on the way back, the platform can hand the text
 * it already holds back to `onValueChange`. This test does that one half-second
 * before the assertion, because that replay is what used to wipe the sections
 * and leave `restoreFocus` without a card to sit on.
 *
 * <p>The chosen card is deliberately the **second** one: the first result also
 * carries `firstResultFocus`, so focusing the first would not prove that the
 * return found the card that was left.
 */
@RunWith(AndroidJUnit4::class)
class SearchTvReturnFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var viewModel: SearchViewModel

    /** Whether the search destination is on screen, as a navigation back stack would flip it. */
    private val searchOnScreen = mutableStateOf(true)

    /** The card the screen asked the application to open; "c2" is the chosen one. */
    private var openedId: String? = null

    @Test
    fun returningFromAChosenCardKeepsTheResultsAndPutsTheFocusBackOnIt() {
        launch()
        submit()

        // The card that will be left, and not the first result.
        compose.onNodeWithText(FIRST_TITLE).assertIsNotFocused()
        compose.onNodeWithText(SECOND_TITLE).assertIsDisplayed()
        compose.onNodeWithText(SECOND_TITLE).performClick()
        compose.waitForIdle()

        // The application opened the chosen card and the destination left.
        compose.runOnIdle { assertEquals("the chosen card was never opened", "c2", openedId) }
        compose.onAllNodesWithText(SECOND_TITLE).assertCountEquals(0)

        // Coming back: the entry's saved state, `chosen` with it.
        compose.runOnIdle { searchOnScreen.value = true }
        compose.waitForIdle()

        // What the field does on the way back: it hands the text it already
        // holds back to the engine (SR-12's trigger).
        compose.runOnIdle { viewModel.onQueryChanged(QUERY, composing = false) }
        compose.waitForIdle()

        compose.onNodeWithText(SECOND_TITLE).assertIsFocused()
        compose.onNodeWithText(FIRST_TITLE).assertIsNotFocused()
        compose.onNodeWithText(FIRST_TITLE).assertIsDisplayed()
    }

    /** Runs the real screen over an engine whose channel section holds two rows. */
    private fun launch() {
        val engine = SearchViewModel(TwoChannelRepository(), FakeActiveSource())
        viewModel = engine
        compose.setContent {
            LumoTvTheme {
                val holder = rememberSaveableStateHolder()
                Box(modifier = Modifier.fillMaxSize().background(LumoColors.Ink)) {
                    if (searchOnScreen.value) {
                        holder.SaveableStateProvider("search") {
                            SearchTvScreen(
                                onPlayChannel = { id, _ ->
                                    openedId = id
                                    searchOnScreen.value = false
                                },
                                onOpenFilm = { searchOnScreen.value = false },
                                onOpenSeries = { searchOnScreen.value = false },
                                viewModel = engine,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Puts the screen on a completed search of two channels.
     *
     * The text is set on the engine rather than typed, for the same reason as
     * [SearchTvFocusTest]: a `performTextInput` would open the platform IME,
     * whose closing commit re-schedules the search on the virtual clock. Where
     * the focus seats a return is the question here, not how a key becomes text.
     */
    private fun submit() {
        compose.runOnIdle {
            viewModel.onQueryChanged(QUERY, composing = false)
            viewModel.onSearchSubmitted()
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(SECOND_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val QUERY = "zz"
        const val FIRST_TITLE = "Alpha TV"
        const val SECOND_TITLE = "Beta TV"
    }

    /** Two channels in one section, so a non-first card can be chosen. */
    private class TwoChannelRepository : SearchRepository {

        override suspend fun search(
            sourceId: String,
            query: String,
            filter: SearchFilter,
            page: Int,
            size: Int,
            present: CataloguePresence,
        ): SearchResults = SearchResults(
            channels = present.channels.takeIf { filter.includes(SearchFilter.Channels) }
                ?.let {
                    LumoResult.Success(
                        SearchPage(
                            listOf(channel("c1", FIRST_TITLE), channel("c2", SECOND_TITLE)),
                            totalElements = 2,
                        ),
                    )
                },
            films = present.films.takeIf { filter.includes(SearchFilter.Films) }
                ?.let { LumoResult.Success(SearchPage(emptyList(), 0)) },
            series = present.series.takeIf { filter.includes(SearchFilter.Series) }
                ?.let { LumoResult.Success(SearchPage(emptyList(), 0)) },
        )

        override suspend fun cataloguePresence(sourceId: String): CataloguePresence = CataloguePresence.all

        private fun channel(id: String, name: String) = Channel(
            id = id,
            sourceId = "s1",
            categoryId = null,
            name = name,
            logoUrl = null,
            number = null,
            quality = null,
            isAdult = false,
        )
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
