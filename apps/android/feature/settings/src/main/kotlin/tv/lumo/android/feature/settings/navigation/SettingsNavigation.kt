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
 *
 * `onOpenSources` is the one way out of this screen into another feature — "My
 * sources", which left both bars with US-017 and is reached from here since. The
 * application holds the wire: a feature never navigates to another feature's route
 * on its own (docs/architecture.md §3).
 */
fun NavGraphBuilder.settingsMobileScreen(onOpenSources: () -> Unit) {
    composable(route = SettingsDestination.route) {
        SettingsMobileScreen(onOpenSources = onOpenSources)
    }
}

fun NavGraphBuilder.settingsTvScreen(onOpenSources: () -> Unit) {
    composable(route = SettingsDestination.route) {
        SettingsTvScreen(onOpenSources = onOpenSources)
    }
}
