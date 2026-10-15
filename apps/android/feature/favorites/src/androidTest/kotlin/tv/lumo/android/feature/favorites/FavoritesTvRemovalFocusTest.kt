package tv.lumo.android.feature.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.designsystem.theme.LumoTvTheme

/**
 * S11-E04 on a television: "Remove from all my favourites" asks with **Cancel
 * focused**, and when the question closes the focus goes back to the card — or,
 * after the removal, to the neighbour that took its place.
 *
 * Drives [FavoritesTvContent] over a plain state, changed the way the view model
 * changes it. Abstract bench entries, no channel.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesTvRemovalFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val removeEverywhere get() = context.getString(R.string.feature_favorites_remove_everywhere)
    private val cancel get() = context.getString(R.string.feature_favorites_cancel)
    private val confirm get() = context.getString(R.string.feature_favorites_remove_everywhere_confirm)

    private val group = FavoriteGroup("group", "Bench", position = 0, isDefault = false)
    private var state by mutableStateOf(FavoritesState())

    @Test
    fun theQuestionFocusesCancelAndCancellingReturnsToTheCard() {
        launch(listOf("a", "b", "c"))

        openOptionsOn("b")
        compose.onNodeWithText(removeEverywhere).performClick()
        compose.waitForIdle()

        // The question is a dialog window of its own: the focus lands once that
        // window has it, a moment after composition.
        compose.waitUntil(timeoutMillis = 3_000) {
            runCatching { compose.onNodeWithText(cancel).assertIsFocused() }.isSuccess
        }
        compose.onNodeWithText(cancel).assertIsFocused()

        compose.onNodeWithText(cancel).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText(name("b")).assertIsFocused()
    }

    @Test
    fun aRemovalPutsTheFocusOnTheNeighbourThatTookItsPlace() {
        launch(listOf("a", "b", "c"))

        openOptionsOn("b")
        compose.onNodeWithText(removeEverywhere).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText(name("c")).assertIsFocused()
    }

    // ---- helpers -------------------------------------------------------------

    private fun launch(ids: List<String>) {
        state = FavoritesState(
            loading = false,
            groups = listOf(group),
            activeSourceId = "source",
            favorites = ids.mapIndexed { index, id -> favorite(id, index) },
        )
        compose.setContent {
            LumoTvTheme {
                FavoritesTvContent(
                    state = state,
                    actions = FavoritesTvActions(
                        onRemoveEverywhere = { favorite ->
                            state = state.copy(removal = GlobalRemoval(favorite.channel, RemovalPhase.Confirming))
                        },
                        onRemovalDismissed = { state = state.copy(removal = null) },
                        onRemoveEverywhereConfirmed = {
                            val channel = state.removal!!.channel
                            state = state.copy(
                                removal = null,
                                removalDone = channel,
                                favorites = state.favorites.filterNot { it.channel.id == channel.id },
                            )
                        },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    /** Puts the remote on the card, then holds `OK` — as the D-pad would. */
    private fun openOptionsOn(id: String) {
        val card = compose.onNodeWithText(name(id))
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        card.performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
    }

    private fun name(id: String) = "Entry $id"

    private fun favorite(id: String, position: Int) = FavoriteChannel(
        favoriteId = "f-$id",
        groupId = group.id,
        position = position,
        channel = Channel(
            id = id,
            sourceId = "source",
            categoryId = null,
            name = name(id),
            logoUrl = null,
            number = null,
            quality = null,
            isAdult = false,
        ),
    )
}
