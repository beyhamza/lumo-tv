package tv.lumo.android.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import tv.lumo.android.feature.settings.SettingsDestination
import tv.lumo.android.feature.settings.SettingsMobileScreen
import tv.lumo.android.feature.settings.SettingsTvScreen

/**
 * Two entry points, one route.
 *
 * Each application installs the surface it needs into its own NavHost. The
 * route string is shared, so a deep link resolves to the same place on the phone
 * and on the television.
 */
fun NavGraphBuilder.settingsMobileScreen() {
    composable(route = SettingsDestination.route) { SettingsMobileScreen() }
}

fun NavGraphBuilder.settingsTvScreen() {
    composable(route = SettingsDestination.route) { SettingsTvScreen() }
}
