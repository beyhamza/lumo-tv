package tv.lumo.android.feature.live

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Watching one channel (US-09).
 *
 * Not a top-level destination — it is never in a bar or a rail, because it is a
 * place one is taken to by choosing a channel and leaves by pressing back. It
 * implements [LumoDestination] anyway so its route is declared next to the
 * feature's other one and stays the same string on both applications.
 *
 * The channel's name travels in the route beside its id. It is presentation and
 * the id would be enough to play — but the contract has no operation returning
 * one channel by id, so a screen that wanted to *name* what it is playing would
 * have to look through a page of the catalogue to find it. The list already knows
 * the name; passing it costs one path segment.
 */
object PlayerDestination : LumoDestination {
    override val route: String = "live/player/{channelId}?name={name}"
    override val titleRes: Int = R.string.feature_live_title

    fun routeFor(channelId: String, name: String?): String =
        "live/player/$channelId?name=${name.orEmpty()}"

    const val ARG_CHANNEL_ID = "channelId"
    const val ARG_NAME = "name"
}
