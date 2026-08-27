plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.live"
}

dependencies {
    implementation(projects.core.data)

    // Paging in Compose. The list reads windows out of SQLite and never holds a
    // whole catalogue: fifteen thousand channels is an ordinary source (US-08).
    implementation(libs.androidx.paging.compose)

    // Channel logos, and nothing else. They are the ones the user's own playlist
    // advertises; Lumo ships no artwork of its own (AGENTS.md §1).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
