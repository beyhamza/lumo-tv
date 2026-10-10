plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
    alias(libs.plugins.lumo.android.room)
}

android {
    namespace = "tv.lumo.android.core.database"

    defaultConfig {
        // The migrations are pinned by MigrationTestHelper on a device (S10B-06):
        // it opens a real SQLite at each exported schema version and runs the
        // hand-written steps against it. The Room plugin ships the committed
        // schemas to the test APK as assets.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.common)

    // Paging reads straight out of Room: the DAO returns a PagingSource, so a
    // 15 000-channel catalogue is never materialised as a List (US-08).
    implementation(libs.androidx.room.paging)
    api(libs.androidx.paging.common)
    implementation(libs.androidx.paging.runtime)

    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // The runner itself: feature modules get it through Compose's ui-test, this
    // module has no Compose to bring it.
    androidTestImplementation(libs.androidx.test.runner)
}
