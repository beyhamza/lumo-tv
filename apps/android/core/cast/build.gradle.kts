plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.library.compose)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android.core.cast"
}

// The Chromecast sender (S16, ADR 0013): the Google Cast SDK behind
// core:player's RemotePlayback. Only the phone application depends on this
// module — the television application ships no Cast SDK, and its player stays
// local (ADR 0013: "Not on Android TV").
dependencies {
    implementation(projects.core.player)

    implementation(libs.play.services.cast.framework)
    // The MediaRouteButton and its device picker.
    implementation(libs.androidx.mediarouter)
    implementation(libs.kotlinx.coroutines.android)
}
