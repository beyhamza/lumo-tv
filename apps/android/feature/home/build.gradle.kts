plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.home"
}

dependencies {
    // The only module this feature reads through, and the reason it needs no
    // other feature: films and series in progress, favourites, recent channels
    // and the active source are all repositories there (US-017).
    implementation(projects.core.data)

    // Channel logos on the phone's rails. They are the ones the user's own
    // playlist advertises; Lumo ships no artwork of its own (AGENTS.md §1).
    // Posters go through `LumoPoster`, which core:designsystem already exposes.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // The television surface draws with androidx.tv: its components carry focus
    // states and the phone library's do not (AGENTS.md §6).
    implementation(libs.androidx.tv.material)
}
