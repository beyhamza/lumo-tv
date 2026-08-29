plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.library.compose)
}

android {
    namespace = "tv.lumo.android.core.designsystem"
}

dependencies {
    // `api` because LumoDestination appears in the signature of the navigation
    // components below: a consumer that calls them needs the type.
    api(projects.core.common)

    // `api`, not `implementation`: this module is the single place the two
    // design languages are declared, and feature modules build their screens
    // out of both. Making them transitive here is what stops eight feature
    // build files from each pinning their own Material dependency.
    api(libs.androidx.compose.material3)
    api(libs.androidx.tv.material)
    api(libs.androidx.compose.ui.tooling.preview)

    // `api`, for the same reason Material is: `LumoPoster` is the one place a
    // poster is drawn, and every feature that draws one goes through it. Coil
    // shares the OkHttp stack rather than opening a second one.
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.lifecycle.runtime.compose)
}
