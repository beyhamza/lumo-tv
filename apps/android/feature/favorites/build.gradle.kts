plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.favorites"
}

dependencies {
    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core module
    // its feature actually touches.
    implementation(projects.core.data)

    // Channel logos, and nothing else. They are the ones the user's own playlist
    // advertises; Lumo ships no artwork of its own (AGENTS.md §1).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
