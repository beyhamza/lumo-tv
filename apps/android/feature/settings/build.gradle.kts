import tv.lumo.buildlogic.lumoEnv

plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.settings"

    buildFeatures {
        // For one field: the page a television's QR code points at (US-05). It
        // comes from `.env` at build time because it differs between a local
        // stack and production — and its origin is also where the web guides
        // live (US-025), so one variable names the website for both.
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "ACTIVATION_URL",
            "\"${lumoEnv("LUMO_ACTIVATION_URL", default = "https://lumo.tv/activate")}\"",
        )
    }
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
