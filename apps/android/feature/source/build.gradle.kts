plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.source"
}

dependencies {
    // The repositories, the typed errors, and the generated `SourceKind` and
    // `ErrorCode` this screen branches on.
    implementation(projects.core.data)

    // BackHandler: "My sources" opens the add flow over its list, and Back has
    // to close that flow rather than leave the destination (US-024). Already in
    // the catalogue and used the same way by the players; no new dependency.
    implementation(libs.androidx.activity.compose)
}
