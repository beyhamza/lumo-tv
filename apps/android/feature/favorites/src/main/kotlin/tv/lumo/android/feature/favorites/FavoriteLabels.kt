package tv.lumo.android.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.FavoriteChannel

/**
 * The list as the screens draw it: a favourite whose channel could not be
 * resolved on this device carries "Details unavailable" for a name (S11-05).
 *
 * Applied once, at the top of a screen, so that every place that names the
 * channel — the row, the options, the removal dialog — says the same thing, and
 * none of them says nothing.
 */
@Composable
internal fun List<FavoriteChannel>.withReadableNames(): List<FavoriteChannel> {
    val unavailable = stringResource(R.string.feature_favorites_details_unavailable)
    return remember(this, unavailable) {
        map { if (it.resolved) it else it.copy(channel = it.channel.copy(name = unavailable)) }
    }
}
