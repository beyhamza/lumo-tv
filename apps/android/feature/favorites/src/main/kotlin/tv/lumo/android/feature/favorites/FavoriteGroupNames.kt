package tv.lumo.android.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.FavoriteGroup

/**
 * What to call a group on screen, on either surface.
 *
 * The server names the group it creates on the first add `Favorites`, in English.
 * `is_default` is what lets a client translate it; the second half of the
 * condition is what stops the translation overriding the user once they have
 * renamed the group.
 */
@Composable
internal fun FavoriteGroup.displayName(): String =
    if (isDefault && name == SERVER_DEFAULT_GROUP_NAME) {
        stringResource(R.string.feature_favorites_default_group)
    } else {
        name
    }

/** The names of [groups], in their order, for a sentence that lists them. */
@Composable
internal fun groupNames(groups: List<FavoriteGroup>): String =
    groups.map { it.displayName() }.joinToString(", ")

/** The name the server gives the default group, verbatim. */
private const val SERVER_DEFAULT_GROUP_NAME = "Favorites"
