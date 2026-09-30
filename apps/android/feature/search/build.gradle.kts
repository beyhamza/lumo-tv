plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.search"
}

dependencies {
    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core module
    // its feature actually touches. This one reads the unified search of S10-01
    // and the active source it is scoped to.
    implementation(projects.core.data)
}
