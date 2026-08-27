plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.auth"
}

dependencies {
    // The only module a feature talks to for data. It brings the generated
    // `ErrorCode` with it, which is what this screen branches on.
    implementation(projects.core.data)
}
