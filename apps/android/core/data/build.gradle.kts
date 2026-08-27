plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
}

android {
    namespace = "tv.lumo.android.core.data"
}

dependencies {
    implementation(projects.core.common)
    // Reads the access token for nothing: the interceptor does that. What this
    // module needs from core:auth is signing out — dropping the session is part
    // of "the account", and the account is a repository here.
    implementation(projects.core.auth)
    implementation(projects.core.database)

    // `api`, not `implementation`, and it is the one deliberate leak in this
    // module: a feature branches on the contract's `ErrorCode`, so that enum is
    // part of the vocabulary this module publishes. Everything else generated —
    // the Retrofit interfaces, the request and response models — stays behind
    // the repositories.
    api(projects.core.network)

    // PagingData appears in this module's signatures (US-08).
    api(libs.androidx.paging.common)

    implementation(libs.retrofit.core)
    implementation(libs.moshi.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.converter.moshi)
}
