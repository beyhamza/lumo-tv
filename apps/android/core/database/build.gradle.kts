plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
    alias(libs.plugins.lumo.android.room)
}

android {
    namespace = "tv.lumo.android.core.database"
}

dependencies {
    implementation(projects.core.common)

    // Paging reads straight out of Room: the DAO returns a PagingSource, so a
    // 15 000-channel catalogue is never materialised as a List (US-08).
    implementation(libs.androidx.room.paging)
    api(libs.androidx.paging.common)
    implementation(libs.androidx.paging.runtime)

    testImplementation(libs.androidx.room.testing)
}
