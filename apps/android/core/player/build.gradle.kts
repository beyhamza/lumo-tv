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

    // The audio-track picker is drawn by the design system, and the mapping from
    // a Media3 track to a row it can draw lives here rather than in each of the
    // three features that plays something. `settings.gradle.kts` forbids a
    // feature depending on a feature; it does not forbid this, and the
    // alternative is the same label logic written three times.
    api(projects.core.designsystem)

    // Media3 stays behind LumoPlayer. Nothing outside this module imports
    // androidx.media3 — that is what lets the phone and the television share one
    // playback implementation and what would let the engine be replaced.
    implementation(libs.androidx.media3.exoplayer)
    // HLS is what IPTV panels serve for live. Progressive files — which is what a
    // film is — need no extra artifact: the MP4 and Matroska extractors ship in
    // media3-exoplayer itself. DASH still does not, and is still not added.
    implementation(libs.androidx.media3.exoplayer.hls)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
}
