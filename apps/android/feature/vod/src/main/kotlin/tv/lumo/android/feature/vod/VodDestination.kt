package tv.lumo.android.feature.vod

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Where this feature sits in either application's navigation.
 *
 * The feature owns its route and its label; the application decides where the
 * destination appears and what it is drawn as. That is what lets one feature
 * module serve both a bottom bar and a D-pad rail without knowing which it is in
 * (docs/architecture.md §3).
 */
object VodDestination : LumoDestination {
    override val route: String = "vod"
    override val titleRes: Int = R.string.feature_vod_title
}
