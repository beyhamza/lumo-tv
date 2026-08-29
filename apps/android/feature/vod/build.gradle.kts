plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.vod"
}

dependencies {
    implementation(projects.core.data)
    // The player stays behind LumoPlayer; this module composes its surface and
    // never imports androidx.media3 (docs/architecture.md §3).
    implementation(projects.core.player)

    // LocalActivity, for the window that owns the system bars while a film is
    // full screen. Nothing else in this module touches an Activity.
    implementation(libs.androidx.activity.compose)

    // Paging in Compose. A catalogue of thirty thousand films is ordinary, and a
    // poster grid holds far more memory per row than a channel list (US-13).
    implementation(libs.androidx.paging.compose)

    // The television surface draws with androidx.tv: its components carry focus
    // states and the phone library's do not (AGENTS.md §6).
    implementation(libs.androidx.tv.material)

    // Posters come through `LumoPoster`, which `core:designsystem` exposes as
    // `api` — so Coil is not declared here. That is the point of putting the one
    // image loader and the one poster component down there.
}
