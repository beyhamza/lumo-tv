plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.series"
}

dependencies {
    implementation(projects.core.data)
    // The player stays behind LumoPlayer; this module composes its surface and
    // never imports androidx.media3 (docs/architecture.md §3).
    implementation(projects.core.player)

    // LocalActivity, for the window that owns the system bars while an episode is
    // full screen.
    implementation(libs.androidx.activity.compose)

    // Paging in Compose. A catalogue of fifty thousand series is ordinary on a
    // real panel, and a poster grid holds far more memory per row than a list.
    implementation(libs.androidx.paging.compose)

    // The television surface draws with androidx.tv (AGENTS.md §6).
    implementation(libs.androidx.tv.material)

    // Posters come through `LumoPoster`, which `core:designsystem` exposes as
    // `api` — so Coil is not declared here.
}
