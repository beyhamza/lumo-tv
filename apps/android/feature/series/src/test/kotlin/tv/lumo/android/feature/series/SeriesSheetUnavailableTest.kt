package tv.lumo.android.feature.series

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.SheetUnavailable
import tv.lumo.android.core.data.WatchlistToggleState
import tv.lumo.android.core.data.model.Series
import tv.lumo.android.core.designsystem.component.LumoWatchlistNote

/**
 * BUG-S11-06-04 on the series sheet: offline with nothing cached, the sheet and
 * its "My list" button say why, and the provider is not blamed for the network.
 */
class SeriesSheetUnavailableTest {

    private val offline = LumoResult.Failure(LumoError.Offline(IOException("lost")))
    private val refused = LumoResult.Failure(LumoError.Unreadable(status = 500, cause = null))

    @Test
    fun `offline with nothing cached is a sheet unavailable offline, and the button says so`() {
        val state = SeriesDetailState(series = null, lastLoad = offline)

        assertThat(state.unavailable).isEqualTo(SheetUnavailable.Offline)
        assertThat(unavailableTitle(state)).isEqualTo(
            R.string.feature_series_offline_title to R.string.feature_series_offline_body,
        )
        val button = WatchlistToggleState().asButtonState(state.unavailable)
        assertThat(button.enabled).isFalse()
        assertThat(button.note).isEqualTo(LumoWatchlistNote.Offline)
    }

    @Test
    fun `offline with the series cached speaks of the episodes, not the provider`() {
        val state = SeriesDetailState(series = series(), lastLoad = offline)

        assertThat(state.unavailable).isNull()
        assertThat(unavailableTitle(state)).isEqualTo(
            R.string.feature_series_episodes_offline_title to R.string.feature_series_offline_body,
        )
    }

    @Test
    fun `a provider that did not answer keeps its own sentence`() {
        val state = SeriesDetailState(series = series(), lastLoad = refused)

        assertThat(unavailableTitle(state)).isEqualTo(
            R.string.feature_series_tree_unavailable_title to R.string.feature_series_tree_unavailable_body,
        )
    }

    private fun series() = Series(
        id = "series",
        sourceId = "source",
        categoryId = null,
        name = "Entry",
        posterUrl = null,
        year = null,
        episodeRunTime = null,
        rating = null,
        plot = null,
        isAdult = false,
    )
}
