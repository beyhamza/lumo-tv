package tv.lumo.android.feature.auth

import tv.lumo.android.core.common.navigation.LumoDestination

/**
 * Creating an account (US-01).
 *
 * A route of its own rather than a mode on [AuthDestination]. The two screens ask
 * for different things, refuse for different reasons and are entered from
 * different places; folding them into one route would mean a parameter that every
 * link, every back stack entry and every future deep link has to carry correctly.
 *
 * **Phone and web only.** US-01 says so, and the reason is the same one that gave
 * the product a device-code flow: an email, a password and a display name typed on
 * a remote control is where people give up (US-05). There is no `signUpTvScreen`,
 * and its absence is the design.
 */
object SignUpDestination : LumoDestination {
    override val route: String = "sign-up"
    override val titleRes: Int = R.string.feature_auth_sign_up_title
}
