package tv.lumo.android.feature.watchlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.feature.watchlist.navigation.WatchlistActions

/**
 * What selecting a card does, the same on both surfaces.
 *
 * An available card opens the film's or the series' **existing** sheet with its
 * `content_id`. An unavailable one has no sheet to open — the content is gone — so
 * it opens the compact panel instead (WL-03): its known title, and the removal.
 */
sealed interface CardSelection {
    data class OpenFilm(val filmId: String) : CardSelection
    data class OpenSeries(val seriesId: String) : CardSelection
    data object OpenPanel : CardSelection
}

fun WatchlistEntry.selection(): CardSelection {
    val id = contentId
    return when {
        !available || id == null -> CardSelection.OpenPanel
        type == WatchlistType.Film -> CardSelection.OpenFilm(id)
        else -> CardSelection.OpenSeries(id)
    }
}

/**
 * The poster a card draws: none for an unavailable item, whose snapshot may point
 * at artwork the source no longer serves — the placeholder says the same thing
 * without a failed request first.
 */
internal val WatchlistEntry.cardPoster: String?
    get() = posterUrl.takeIf { available }

internal fun select(
    entry: WatchlistEntry,
    actions: WatchlistActions,
    onPanel: (WatchlistEntry) -> Unit,
) = when (val selection = entry.selection()) {
    is CardSelection.OpenFilm -> actions.onOpenFilm(selection.filmId)
    is CardSelection.OpenSeries -> actions.onOpenSeries(selection.seriesId)
    CardSelection.OpenPanel -> onPanel(entry)
}

@Composable
internal fun typeLabel(type: WatchlistType): String = stringResource(
    when (type) {
        WatchlistType.Film -> R.string.feature_watchlist_type_film
        WatchlistType.Series -> R.string.feature_watchlist_type_series
    },
)

@Composable
internal fun filterLabel(filter: WatchlistFilter): String = stringResource(
    when (filter) {
        WatchlistFilter.All -> R.string.feature_watchlist_filter_all
        WatchlistFilter.Films -> R.string.feature_watchlist_filter_films
        WatchlistFilter.Series -> R.string.feature_watchlist_filter_series
    },
)

@Composable
internal fun filterEmptyLabel(filter: WatchlistFilter): String = stringResource(
    when (filter) {
        WatchlistFilter.Series -> R.string.feature_watchlist_filter_empty_series
        else -> R.string.feature_watchlist_filter_empty_films
    },
)

@Composable
internal fun cardDescription(entry: WatchlistEntry): String = stringResource(
    if (entry.available) {
        R.string.feature_watchlist_card_description
    } else {
        R.string.feature_watchlist_card_unavailable_description
    },
    entry.title,
    typeLabel(entry.type),
)
