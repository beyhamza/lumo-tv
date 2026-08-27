import tv.lumo.buildlogic.lumoEnv

plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.auth"

    buildFeatures {
        // For one field: the Google web client ID. It comes from `.env` at build
        // time rather than from a constant in the source, because it differs per
        // environment and because AGENTS.md §5 keeps that kind of value out of
        // the repository.
        buildConfig = true
    }

    defaultConfig {
        // Empty is a SUPPORTED state, and the one in a fresh checkout: no OAuth
        // client is configured, so the Google button is not drawn at all. A
        // button that is always going to fail is worse than no button.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${lumoEnv("LUMO_GOOGLE_WEB_CLIENT_ID")}\"",
        )
    }
}

dependencies {
    // The only module a feature talks to for data. It brings the generated
    // `ErrorCode` with it, which is what this screen branches on.
    implementation(projects.core.data)

    // QR encoding for the television's activation code (US-05). Pure Java: it
    // produces a matrix of bits, and the screen paints it on a Canvas.
    implementation(libs.zxing.core)

    // The activation screen is a TV surface, so it draws with androidx.tv.
    implementation(libs.androidx.tv.material)

    // Google sign-in (US-03). Credential Manager is the platform's own account
    // picker: this application never reads the device's account list, and never
    // sees anything but the id_token the picker hands back.
    // LocalActivity: the account picker opens a window over the current one
    // and needs the activity it sits on, not the application context.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
}
