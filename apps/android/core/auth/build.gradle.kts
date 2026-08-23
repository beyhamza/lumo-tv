plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android.core.auth"
}

dependencies {
    implementation(projects.core.common)

    // Proto-less DataStore: this module stores an encrypted blob, so there is
    // nothing for Preferences or protobuf to structure. See SessionSerializer.
    implementation(libs.androidx.datastore)
}
