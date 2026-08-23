plugins {
    alias(libs.plugins.lumo.android.feature)
}

dependencies {
    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core
    // module its feature actually touches.
    implementation(project(":core:auth"))
}

android {
    namespace = "tv.lumo.android.feature.settings"
}
