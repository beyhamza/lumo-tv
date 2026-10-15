plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.favorites"

    defaultConfig {
        // Where the focus lands when the removal question closes is a property of
        // the focus system on a device: pinned by an instrumented Compose test on a
        // television, like "My list"'s panel.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)

    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core module
    // its feature actually touches.
    implementation(projects.core.data)

    // Channel logos, and nothing else. They are the ones the user's own playlist
    // advertises; Lumo ships no artwork of its own (AGENTS.md §1).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
