plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.search"

    defaultConfig {
        // The D-pad focus of the TV search field is a property of the focus
        // system on a device, not of a pure function: S10-05 pins it with an
        // instrumented Compose test on a television.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)

    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core module
    // its feature actually touches. This one reads the unified search of S10-01
    // and the active source it is scoped to.
    implementation(projects.core.data)
}
