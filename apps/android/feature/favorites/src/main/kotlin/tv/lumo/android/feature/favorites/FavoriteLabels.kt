package tv.lumo.android.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
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

/**
 * What the confirmation of a group deletion says about the group's channels.
 *
 * An empty group gets its own sentence (BUG-S11-06-08): Android does not use
 * `quantity="zero"` for English or French, so a plural would read "Its 0
 * channels will move…" — and French files 0 under `one`, "Sa 0 chaîne ira…".
 */
internal sealed interface GroupDeletionMessage {
    data object Empty : GroupDeletionMessage
    data class Moves(val count: Int) : GroupDeletionMessage

    companion object {
        fun of(count: Int): GroupDeletionMessage = if (count <= 0) Empty else Moves(count)
    }
}

/** The body of the group deletion's confirmation, on both surfaces. */
@Composable
internal fun groupDeletionMessage(count: Int, defaultGroupName: String): String =
    when (val message = GroupDeletionMessage.of(count)) {
        GroupDeletionMessage.Empty -> stringResource(R.string.feature_favorites_delete_body_empty)
        is GroupDeletionMessage.Moves -> pluralStringResource(
            R.plurals.feature_favorites_delete_body,
            message.count,
            message.count,
            defaultGroupName,
        )
    }
