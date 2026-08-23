plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.library.compose)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android.core.player"
}

dependencies {
    implementation(projects.core.common)

    // Media3 stays behind LumoPlayer. Nothing outside this module imports
    // androidx.media3 — that is what lets the phone and the television share one
    // playback implementation and what would let the engine be replaced.
    implementation(libs.androidx.media3.exoplayer)
    // HLS is what IPTV panels serve. Progressive/DASH sources are added the day
    // a real user needs one, not speculatively.
    implementation(libs.androidx.media3.exoplayer.hls)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
}
