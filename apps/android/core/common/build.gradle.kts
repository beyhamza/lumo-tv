plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
