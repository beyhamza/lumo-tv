plugins {
    alias(libs.plugins.lumo.android.application)
    alias(libs.plugins.lumo.android.application.compose)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android"

    defaultConfig {
        // ADR 0004 — phone and tablet listing.
        applicationId = "tv.lumo.android"
    }
}

// This module is a shell: a manifest, an Activity, a NavHost and a theme. Any
// logic that appears here is logic app-tv cannot use, which makes it a defect
// (docs/architecture.md §3).
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.auth)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.player)
    // Chromecast sender (S16, ADR 0013): the phone only. app-tv does not depend on it.
    implementation(projects.core.cast)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.source)
    implementation(projects.feature.live)
    implementation(projects.feature.favorites)
    implementation(projects.feature.watchlist)
    implementation(projects.feature.vod)
    implementation(projects.feature.series)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    // FragmentActivity: the Cast device picker is a DialogFragment (S16-01).
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
}
