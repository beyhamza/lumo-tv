plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.watchlist"

    defaultConfig {
        // Where the focus lands when the unavailable-item panel closes is a
        // property of the focus system on a device: pinned by an instrumented
        // Compose test on a television, like the search screen's.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)

    // Stated here, not inherited: "My list" reads the watchlist (US-022) and the
    // active source it is scoped to.
    implementation(projects.core.data)

    implementation(libs.androidx.tv.material)
}
