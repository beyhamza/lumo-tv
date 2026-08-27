plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.settings"
}

dependencies {
    // Stated here, not inherited: the feature convention plugin wires only
    // core:common and core:designsystem, so a build file names every core
    // module its feature actually touches.
    //
    // core:auth is gone from this list on purpose. The session is read through
    // AccountRepository now, which is also what ends it — a screen that reached
    // for SessionManager directly would be a second place that knows how a
    // session is opened and closed.
    implementation(projects.core.data)
}
