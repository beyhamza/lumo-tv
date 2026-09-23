plugins {
    alias(libs.plugins.lumo.android.feature)
}

android {
    namespace = "tv.lumo.android.feature.auth"

    // No `buildConfig` any more. The one field this module read from `.env` was
    // the Google web client ID, and the Google button left the screens with the
    // 0.2.0 scope (US-025, decision of 17 September 2026). The server side of
    // that flow is a deliberate debt (docs/backlog/dette.md §1); nothing here
    // calls it.
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

    // Credential Manager and the Google identity library used to follow, for
    // the account picker behind "Continue with Google". Gone with the button:
    // a dependency nothing calls is one more thing R8 has to be told about.
}
