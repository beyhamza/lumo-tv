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
}
