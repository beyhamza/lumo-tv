plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.live"

    defaultConfig {
        // The instrumented focus proof for GD-07 (BUG-S9-06-01-01) launches the
        // composable in a bare ComponentActivity; the runner comes from the
        // compose test stack the convention plugin already wires.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)

    implementation(projects.core.data)
    // The player itself stays behind LumoPlayer; this module composes its surface
    // and never imports androidx.media3 (docs/architecture.md §3).
    implementation(projects.core.player)

    // LocalActivity, for the window that owns the system bars while a video is
    // full screen. Nothing else in this module touches an Activity.
    implementation(libs.androidx.activity.compose)

    // Paging in Compose. The list reads windows out of SQLite and never holds a
    // whole catalogue: fifteen thousand channels is an ordinary source (US-08).
    implementation(libs.androidx.paging.compose)

    // The television surface draws with androidx.tv: its components carry focus
    // states and the phone library's do not (AGENTS.md §6).
    implementation(libs.androidx.tv.material)

    // Channel logos, and nothing else. They are the ones the user's own playlist
    // advertises; Lumo ships no artwork of its own (AGENTS.md §1).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
