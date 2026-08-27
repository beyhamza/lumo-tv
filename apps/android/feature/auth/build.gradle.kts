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

    // QR encoding for the television's activation code (US-05). Pure Java: it
    // produces a matrix of bits, and the screen paints it on a Canvas.
    implementation(libs.zxing.core)

    // The activation screen is a TV surface, so it draws with androidx.tv.
    implementation(libs.androidx.tv.material)
}
