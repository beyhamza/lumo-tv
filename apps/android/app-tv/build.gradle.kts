plugins {
    alias(libs.plugins.lumo.android.application)
    alias(libs.plugins.lumo.android.application.compose)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.androidtv"

    defaultConfig {
        // ADR 0004 — separate Android TV listing. A rejection on one listing
        // must not block the other.
        applicationId = "tv.lumo.androidtv"
    }
}

// Same dependency list as app-mobile, on purpose: everything below the UI is
// shared. What differs is the Activity, the theme and the navigation shell.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.auth)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.player)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.auth)
    implementation(projects.feature.source)
    implementation(projects.feature.live)
    implementation(projects.feature.vod)
    implementation(projects.feature.series)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.material)
}
