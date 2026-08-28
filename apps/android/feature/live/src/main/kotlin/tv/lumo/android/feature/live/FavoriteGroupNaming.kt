package tv.lumo.android.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.FavoriteGroup

/**
 * The name the server gives the group it creates on the first add, verbatim.
 *
 * A constant rather than a literal in two screens: the phone and the television
 * both have to recognise it, and a string spelled out twice is a string that gets
 * misspelled once.
 */
internal const val SERVER_DEFAULT_GROUP_NAME: String = "Favorites"

/**
 * What to call a favourite group on screen.
 *
 * The server has to name the group it creates on the first add, and names it
 * `Favorites`, in English — a user-visible string in one language.
 * `FavoriteGroup.is_default` is what lets a client translate it, and it is a flag
 * rather than a sentinel in the name for exactly this reason: the name belongs to
 * the user the moment they change it, and a client must still know which group is
 * the default afterwards.
 *
 * Hence the two halves of the condition. Translate while the flag is true **and**
 * the name is still the server's own; once somebody has renamed the group on their
 * phone, their name wins on their television too.
 *
 * `feature:favorites` carries its own copy of this, deliberately: it is a different
 * module with its own string resources, and moving a `@Composable` that reads
 * `R.string` down into `core/` would mean moving the wording out of the feature
 * that owns it.
 */
@Composable
internal fun FavoriteGroup.displayName(): String =
    if (isDefault && name == SERVER_DEFAULT_GROUP_NAME) {
        stringResource(R.string.feature_live_favorite_default_group)
    } else {
        name
    }
