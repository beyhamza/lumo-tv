package tv.lumo.android.core.common.navigation

import androidx.annotation.StringRes

/**
 * A top-level destination, described by the feature that owns it.
 *
 * This exists so an application module can build a navigation bar or a rail
 * without importing eight feature `R` classes and without knowing what a route
 * string looks like. The feature owns its route and its label; the application
 * owns the order they appear in and the shape they are drawn as — a bottom bar
 * on the phone, a side rail on the television (docs/architecture.md §3).
 *
 * A feature never depends on another feature, so this interface lives in
 * `core:common` where both sides can see it.
 */
interface LumoDestination {

    /** The navigation route. Unique across the product. */
    val route: String

    /**
     * The label, from the owning feature's own `strings.xml`.
     *
     * A string resource rather than a `String`: labels are resolved at
     * composition time in the user's locale, and FR and EN ship together from
     * the first screen (AGENTS.md §4).
     */
    @get:StringRes
    val titleRes: Int
}
